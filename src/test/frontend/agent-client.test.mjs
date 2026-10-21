import test from "node:test";
import assert from "node:assert/strict";
import { createSseParser, consumeAgentStream, streamAgentChat } from "../../main/resources/static/user-frontend/sse-client.mjs";
import { formatMessage } from "../../main/resources/static/user-frontend/message-format.mjs";
import { canConfirmDraft, confirmedDraftRequest, candidatePayload } from "../../main/resources/static/user-frontend/agent-policy.mjs";

const frame = (event, value, newline = "\n") => `event:${event}${newline}data:${JSON.stringify(value)}${newline}${newline}`;
const meta = frame("meta", { conversationId: "chat-a" });
const done = frame("done", { conversationId: "chat-a", reply: "最终回复", proposalToolResult: null });
function response(text, byteSize = 13) {
  const bytes = new TextEncoder().encode(text);
  return new Response(new ReadableStream({ start(controller) {
    for (let offset = 0; offset < bytes.length; offset += byteSize) controller.enqueue(bytes.slice(offset, offset + byteSize));
    controller.close();
  } }), { headers: { "Content-Type": "text/event-stream;charset=UTF-8" } });
}
function draft(changes = {}) {
  return { status: "SUCCESS", draftId: "draft-a", expireAt: "2099-10-21T15:00:00",
    draft: { items: [{ dishName: "热豆浆", unitPrice: 8, quantity: 2, subTotal: 16 }], totalAmount: 16, budget: 20, overBudget: false }, ...changes };
}

test("SSE parser handles split CRLF, comments and multiple data lines", () => {
  const events = [];
  const parser = createSseParser((event) => events.push(event));
  for (const char of ": heartbeat\r\nevent: delta\r\ndata: first\r\ndata: second\r\n\r\n") parser.push(char);
  parser.end();
  assert.deepEqual(events, [{ event: "delta", data: "first\nsecond" }]);
});
test("SSE parser does not dispatch incomplete events at EOF", () => {
  const events = [], parser = createSseParser((event) => events.push(event));
  parser.push("event:delta\ndata:partial\n"); parser.end();
  assert.deepEqual(events, []);
});
test("UTF-8 characters split at every byte decode correctly before authoritative done", async () => {
  const deltas = [];
  const result = await consumeAgentStream(response(meta + frame("delta", { text: "你好\n🍲" }) + done, 1), { onDelta: (text) => deltas.push(text) });
  assert.deepEqual(deltas, ["你好\n🍲"]);
  assert.equal(result.reply, "最终回复");
});
test("JSON preflight failures preserve the HTTP error for login handling", async () => {
  await assert.rejects(consumeAgentStream(new Response(JSON.stringify({ code: 401, msg: "登录失效" }),
    { status: 401, headers: { "Content-Type": "application/json" } })), (error) => error.status === 401 && error.message === "登录失效");
});
test("proxy HTML failure is reported without displaying server HTML", async () => {
  await assert.rejects(consumeAgentStream(new Response("<html>private proxy error</html>", { status: 503 })),
    (error) => error.status === 503 && !error.message.includes("private"));
});
test("model error is terminal and never calls onDone", async () => {
  let count = 0;
  await assert.rejects(consumeAgentStream(response(meta + frame("error", { code: 504, msg: "生成失败" })), { onDone: () => count++ }),
    (error) => error.status === 504);
  assert.equal(count, 0);
});
test("EOF after partial reply is not a completed response", async () => {
  await assert.rejects(consumeAgentStream(response(meta + frame("delta", { text: "尚未完成" }))), /回复尚未完成/);
});
test("unknown conversation ID from server is rejected", async () => {
  await assert.rejects(consumeAgentStream(response(meta + done), { conversationId: "chat-other" }), /编号不一致/);
});
test("done must match metadata and contain a reply", async () => {
  await assert.rejects(consumeAgentStream(response(meta + frame("done", { conversationId: "chat-other", reply: "wrong" }))), /完成信息异常/);
  await assert.rejects(consumeAgentStream(response(meta + frame("done", { conversationId: "chat-a" }))), /完成信息异常/);
});
test("malformed JSON and text before metadata fail closed", async () => {
  await assert.rejects(consumeAgentStream(response("event:delta\ndata:{broken}\n\n")), /格式异常/);
  await assert.rejects(consumeAgentStream(response(frame("delta", { text: "early" }) + done)), /片段异常/);
});
test("POST sends trusted auth header, same-origin cookies and explicit confirmation", async () => {
  let request;
  const controller = new AbortController();
  await streamAgentChat({ message: "确认", token: "fixture-only-token", conversationId: "chat-a", confirmedDraftId: "draft-a", signal: controller.signal,
    fetchImpl: async (url, options) => { request = { url, options }; return response(meta + done); } });
  assert.equal(request.url, "/agent/chat/stream");
  assert.equal(request.options.method, "POST");
  assert.equal(request.options.credentials, "same-origin");
  assert.equal(request.options.headers["X-User-Token"], "fixture-only-token");
  assert.equal(request.options.signal, controller.signal);
  assert.deepEqual(JSON.parse(request.options.body), { message: "确认", conversationId: "chat-a", confirmedDraftId: "draft-a" });
});
test("ordinary guest chat omits identity and confirmation fields", async () => {
  await streamAgentChat({ message: "看看菜单", fetchImpl: async (_url, options) => {
    assert.deepEqual(JSON.parse(options.body), { message: "看看菜单" });
    assert.equal(options.headers["X-User-Token"], undefined);
    return response(meta + done);
  } });
});
test("network failure never automatically repeats a write-capable request", async () => {
  let calls = 0;
  await assert.rejects(streamAgentChat({ message: "确认", confirmedDraftId: "draft-a", fetchImpl: async () => { calls++; throw new TypeError("offline"); } }), /offline/);
  assert.equal(calls, 1);
});
test("confirmation requires valid quote, expiry and explicit non-over-budget flag", () => {
  assert.equal(canConfirmDraft(draft()), true);
  assert.equal(canConfirmDraft(draft({ status: "BUDGET_EXCEEDED" })), false);
  assert.equal(canConfirmDraft(draft({ expireAt: "2000-01-01T00:00:00" })), false);
  assert.equal(canConfirmDraft(draft({ draftId: null })), false);
  assert.equal(canConfirmDraft(draft({ draft: { ...draft().draft, overBudget: true } })), false);
  assert.equal(canConfirmDraft(draft({ draft: { ...draft().draft, overBudget: undefined } })), false);
});
test("confirm action always carries exact quote ID; stale quotes cannot be submitted", () => {
  const payload = confirmedDraftRequest(draft());
  assert.equal(payload.confirmedDraftId, "draft-a");
  assert.match(payload.message, /draft-a/);
  assert.throws(() => confirmedDraftRequest(draft({ expireAt: "2000-01-01T00:00:00" })), /重新试算/);
});
test("profile PUT preserves both fields including null and rejects incomplete candidates", () => {
  assert.deepEqual(candidatePayload({ status: "SUCCESS", proposal: { spicyLevel: null, preferenceText: "少油" } }), { spicyLevel: null, preferenceText: "少油" });
  assert.deepEqual(candidatePayload({ status: "SUCCESS", proposal: { spicyLevel: "mild", preferenceText: null } }), { spicyLevel: "mild", preferenceText: null });
  assert.throws(() => candidatePayload({ status: "SUCCESS", proposal: { spicyLevel: "mild" } }), /不完整/);
  assert.throws(() => candidatePayload({ status: "LOGIN_REQUIRED", proposal: { spicyLevel: "mild", preferenceText: null } }), /不完整/);
});
test("model HTML and executable links remain inert text", () => {
  const rendered = formatMessage('<img src=x onerror="alert(1)">\n<script>alert(1)</script>\n[click](javascript:alert(1))\n**菜单**');
  assert.ok(!rendered.includes("<img"));
  assert.ok(!rendered.includes("<script"));
  assert.ok(!rendered.includes("<a "));
  assert.ok(rendered.includes("&lt;img"));
  assert.ok(rendered.includes("<strong>菜单</strong>"));
});
test("Markdown tables, lists and code preserve safe meal details", () => {
  const rendered = formatMessage("| 菜品 | 小计 |\n|---|---|\n| 热豆浆 | 16元 |\n\n- **不辣**\n\n```\n<quote>\n```");
  assert.ok(rendered.includes("<table>"));
  assert.ok(rendered.includes("<td>热豆浆</td>"));
  assert.ok(rendered.includes("<li><strong>不辣</strong></li>"));
  assert.ok(rendered.includes("&lt;quote&gt;"));
});
