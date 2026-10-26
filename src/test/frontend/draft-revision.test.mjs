import test from "node:test";
import assert from "node:assert/strict";
import { DraftRevisionError, draftRevisionPayload, requestDraftRevision } from "../../main/resources/static/user-frontend/draft-revision.mjs";

const items = [{ dishNumber: 7, quantity: 2 }, { dishNumber: 9, quantity: 1 }];
function revision(changes = {}) {
  return { draftId: "draft-new", expireAt: "2099-10-21T15:00:00", draft: {
    items: [{ dishNumber: 7, dishName: "时蔬", quantity: 2, unitPrice: "12.30", subTotal: "24.60" },
      { dishNumber: 9, dishName: "豆腐汤", quantity: 1, unitPrice: "15.00", subTotal: "15.00" }],
    totalAmount: "39.60", budget: "50.00", overBudget: false,
  }, ...changes };
}
function success(data = revision()) { return Response.json({ code: 0, msg: "success", data }); }

test("revision payload only carries authoritative dish IDs and edited quantities", () => {
  assert.deepEqual(draftRevisionPayload([{ ...items[0], price: 1, draftId: "wrong", ownerKey: "wrong" }, items[1]]), { orderItems: items });
});
test("revision payload rejects empty, duplicate, unsafe IDs and out-of-range quantities", () => {
  for (const value of [null, [], Array.from({ length: 21 }, (_, i) => ({ dishNumber: i + 1, quantity: 1 })),
    [items[0], items[0]], [{ dishNumber: 0, quantity: 1 }], [{ dishNumber: Number.MAX_SAFE_INTEGER + 1, quantity: 1 }],
    [{ dishNumber: 7, quantity: 0 }], [{ dishNumber: 7, quantity: 51 }], [{ dishNumber: 7, quantity: 1.5 }], [{ dishNumber: 7, quantity: "2" }]]) {
    assert.throws(() => draftRevisionPayload(value), DraftRevisionError);
  }
  assert.deepEqual(draftRevisionPayload([{ dishNumber: 1, quantity: 50 }]), { orderItems: [{ dishNumber: 1, quantity: 50 }] });
});
test("guest revisions retain same-origin Session and omit identity and price fields", async () => {
  let request;
  await requestDraftRevision({ draftId: "draft/old?", items, fetchImpl: async (url, init) => { request = { url, ...init }; return success(); } });
  assert.equal(request.url, "/order-drafts/draft%2Fold%3F/revisions");
  assert.equal(request.method, "POST");
  assert.equal(request.credentials, "same-origin");
  assert.equal(request.headers["X-User-Token"], undefined);
  assert.deepEqual(JSON.parse(request.body), { orderItems: items });
});
test("registered revisions send the captured token and cancellation signal", async () => {
  const controller = new AbortController();
  await requestDraftRevision({ baseUrl: "/api", token: "registered-token", draftId: "draft-old", items, signal: controller.signal,
    fetchImpl: async (url, init) => { assert.equal(url, "/api/order-drafts/draft-old/revisions"); assert.equal(init.headers["X-User-Token"], "registered-token"); assert.equal(init.signal, controller.signal); return success(); } });
});
test("new quote preserves the server ID and monetary values without browser sums", async () => {
  const data = revision();
  data.draft.totalAmount = "41.23";
  const result = await requestDraftRevision({ draftId: "draft-old", items, fetchImpl: async () => success(data) });
  assert.equal(result.status, "SUCCESS");
  assert.equal(result.draftId, "draft-new");
  assert.equal(result.draft.totalAmount, "41.23");
  assert.equal(result.draft.items[0].subTotal, "24.60");
});
test("over-budget revisions still return an editable authoritative new quote", async () => {
  const data = revision(); data.draft.overBudget = true;
  const result = await requestDraftRevision({ draftId: "draft-old", items, fetchImpl: async () => success(data) });
  assert.equal(result.status, "BUDGET_EXCEEDED"); assert.equal(result.draft.overBudget, true);
});
test("invalid success bodies are uncertain and cannot reuse the old draft ID", async () => {
  const variants = [null, { ...revision(), draftId: "draft-old" }, { ...revision(), expireAt: "invalid" }];
  for (const mutate of [q => q.overBudget = null, q => q.totalAmount = "NaN", q => q.items.pop(),
    q => q.items[0].quantity = 3, q => q.items[0].dishNumber = 123, q => q.items[0].unitPrice = -1]) {
    const data = revision(); mutate(data.draft); variants.push(data);
  }
  for (const data of variants) await assert.rejects(requestDraftRevision({ draftId: "draft-old", items, fetchImpl: async () => success(data) }), e => e.uncertain === true && e.status === 200);
});
test("business rejections preserve code and status without automatic retry", async () => {
  for (const status of [400, 401, 403, 404, 409]) {
    let calls = 0;
    await assert.rejects(requestDraftRevision({ draftId: "draft-old", items, fetchImpl: async () => { calls++; return Response.json({ code: status, msg: "该报价已被新报价替换，请核对最新报价" }, { status }); } }),
      e => e.status === status && e.code === status && e.uncertain === false && /新报价/.test(e.message));
    assert.equal(calls, 1);
  }
});
test("network, server and invalid JSON failures are not retried or exposed as HTML", async () => {
  for (const fetcher of [async () => { throw new Error("network down"); }, async () => new Response("<html>secret proxy error</html>", { status: 503 }), async () => new Response("broken JSON", { status: 200 })]) {
    let calls = 0;
    await assert.rejects(requestDraftRevision({ draftId: "draft-old", items, fetchImpl: async (...args) => { calls++; return fetcher(...args); } }),
      e => e.uncertain === true && !/secret|<html>|broken JSON|network down/.test(e.message));
    assert.equal(calls, 1);
  }
});
test("unrecognized success envelopes are uncertain", async () => {
  for (const body of [null, {}, { code: "0", data: revision() }]) {
    await assert.rejects(requestDraftRevision({ draftId: "draft-old", items, fetchImpl: async () => Response.json(body) }), e => e.uncertain === true);
  }
});
test("local validation never starts a revision request", async () => {
  let calls = 0;
  await assert.rejects(requestDraftRevision({ draftId: "", items, fetchImpl: async () => { calls++; return success(); } }), e => e.uncertain === false);
  await assert.rejects(requestDraftRevision({ draftId: "draft-old", items: [], fetchImpl: async () => { calls++; return success(); } }), e => e.uncertain === false);
  assert.equal(calls, 0);
});
