/** SSE is framed by blank lines, not by network chunks. No automatic retries. */
export class ChatStreamError extends Error {
  constructor(message, status = 0) {
    super(message);
    this.name = "ChatStreamError";
    this.status = status;
  }
}

export function createSseParser(onEvent) {
  let buffer = "", event = "", data = [];
  function line(value) {
    if (value === "") {
      if (data.length) onEvent({ event: event || "message", data: data.join("\n") });
      event = "";
      data = [];
      return;
    }
    if (value.startsWith(":")) return;
    const colon = value.indexOf(":");
    const field = colon < 0 ? value : value.slice(0, colon);
    let content = colon < 0 ? "" : value.slice(colon + 1);
    if (content.startsWith(" ")) content = content.slice(1);
    if (field === "event") event = content;
    if (field === "data") data.push(content);
  }
  return {
    push(text) {
      buffer += text;
      let end;
      while ((end = buffer.search(/[\r\n]/)) >= 0) {
        // A CR at the chunk boundary may be the first half of CRLF.
        if (buffer[end] === "\r" && end === buffer.length - 1) break;
        const width = buffer[end] === "\r" && buffer[end + 1] === "\n" ? 2 : 1;
        const value = buffer.slice(0, end);
        buffer = buffer.slice(end + width);
        line(value);
      }
    },
    end() {
      if (buffer.endsWith("\r")) {
        line(buffer.slice(0, -1));
        buffer = "";
      }
      // An unfinished event without a blank separator is not dispatched.
    },
  };
}

export async function consumeAgentStream(response, handlers = {}) {
  const type = response.headers.get("Content-Type") || "";
  if (!response.ok || !type.includes("text/event-stream")) {
    let body;
    try { body = await response.json(); } catch { /* May be a proxy error page. */ }
    throw new ChatStreamError(body?.msg || "暂时无法开始回复，请稍后再试", response.status);
  }
  if (!response.body) throw new ChatStreamError("浏览器无法读取流式回复");

  let conversationId = null, done = null;
  const parser = createSseParser(({ event, data }) => {
    let payload;
    try { payload = JSON.parse(data); } catch { throw new ChatStreamError("回复数据格式异常"); }
    if (event === "error") throw new ChatStreamError(payload.msg || "本次回复生成失败", payload.code);
    if (event === "meta") {
      if (conversationId || typeof payload.conversationId !== "string" || !payload.conversationId) {
        throw new ChatStreamError("会话信息异常");
      }
      if (handlers.conversationId && payload.conversationId !== handlers.conversationId) {
        throw new ChatStreamError("返回的会话编号不一致");
      }
      conversationId = payload.conversationId;
      handlers.onMeta?.(payload);
    } else if (event === "delta") {
      if (!conversationId || typeof payload.text !== "string") throw new ChatStreamError("回复片段异常");
      if (!done) handlers.onDelta?.(payload.text);
    } else if (event === "done") {
      if (!conversationId || payload.conversationId !== conversationId || typeof payload.reply !== "string" || done) {
        throw new ChatStreamError("回复完成信息异常");
      }
      done = payload;
      handlers.onDone?.(payload);
    }
  });
  const reader = response.body.getReader();
  const decoder = new TextDecoder("utf-8", { fatal: true });
  try {
    while (!done) {
      const chunk = await reader.read();
      if (chunk.done) {
        parser.push(decoder.decode());
        parser.end();
        break;
      }
      parser.push(decoder.decode(chunk.value, { stream: true }));
    }
    if (!done) throw new ChatStreamError("连接已结束，回复尚未完成");
    return done;
  } finally {
    try { await reader.cancel(); } catch { /* A disconnected stream may already be closed. */ }
    reader.releaseLock();
  }
}

export async function streamAgentChat(options) {
  const { baseUrl = "", token, message, conversationId, confirmedDraftId,
    signal, fetchImpl = fetch, ...handlers } = options;
  const payload = { message };
  if (conversationId) payload.conversationId = conversationId;
  if (confirmedDraftId) payload.confirmedDraftId = confirmedDraftId;
  const response = await fetchImpl(`${baseUrl}/agent/chat/stream`, {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json", Accept: "text/event-stream",
      ...(token ? { "X-User-Token": token } : {}) },
    body: JSON.stringify(payload), signal,
  });
  return consumeAgentStream(response, { ...handlers, conversationId });
}
