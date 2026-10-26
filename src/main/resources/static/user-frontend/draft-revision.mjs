/** Draft revisions are write-capable requests. A failed/uncertain result is never replayed. */
export class DraftRevisionError extends Error {
  constructor(message, { status = 0, code = null, uncertain = false, cause } = {}) {
    super(message, cause === undefined ? undefined : { cause });
    this.name = "DraftRevisionError";
    this.status = status;
    this.code = code;
    this.uncertain = uncertain;
  }
}

const validNumber = (value) => Number.isSafeInteger(value) && value > 0;
const validQuantity = (value) => Number.isInteger(value) && value >= 1 && value <= 50;
const validAmount = (value) => (typeof value === "number" || (typeof value === "string" && value.trim() !== ""))
  && Number.isFinite(Number(value)) && Number(value) >= 0;

export function draftRevisionPayload(items) {
  if (!Array.isArray(items) || items.length < 1 || items.length > 20) {
    throw new DraftRevisionError("请保留 1 至 20 道菜后重新报价", { code: 400 });
  }
  const seen = new Set();
  const orderItems = items.map((item) => {
    if (!item || !validNumber(item.dishNumber) || !validQuantity(item.quantity) || seen.has(item.dishNumber)) {
      throw new DraftRevisionError("菜品编号不能重复，每道菜请设置 1 至 50 份", { code: 400 });
    }
    seen.add(item.dishNumber);
    return { dishNumber: item.dishNumber, quantity: item.quantity };
  });
  return { orderItems };
}

function normalizeRevision(data, sourceId, submitted) {
  const quote = data?.draft;
  const expected = new Map(submitted.map((item) => [item.dishNumber, item.quantity]));
  if (!data || typeof data.draftId !== "string" || !data.draftId.trim() || data.draftId === sourceId
      || typeof data.expireAt !== "string" || !Number.isFinite(Date.parse(data.expireAt))
      || !quote || typeof quote.overBudget !== "boolean" || !validAmount(quote.totalAmount)
      || !(quote.budget === null || validAmount(quote.budget))
      || !Array.isArray(quote.items) || quote.items.length !== submitted.length) return null;
  const seen = new Set();
  for (const item of quote.items) {
    if (!item || !validNumber(item.dishNumber) || seen.has(item.dishNumber)
        || !validQuantity(item.quantity) || expected.get(item.dishNumber) !== item.quantity
        || typeof item.dishName !== "string" || !item.dishName.trim()
        || !validAmount(item.unitPrice) || !validAmount(item.subTotal)) return null;
    seen.add(item.dishNumber);
  }
  // Preserve authoritative prices/subtotals exactly; the browser does not recompute the quote.
  return { status: quote.overBudget ? "BUDGET_EXCEEDED" : "SUCCESS",
    draftId: data.draftId, expireAt: data.expireAt, draft: quote };
}

function responseMessage(body, fallback) {
  const message = body?.msg;
  return typeof message === "string" && message.trim() && message.length <= 500 && !/[<>]/.test(message)
    ? message : fallback;
}

export async function requestDraftRevision({ baseUrl = "", token, draftId, items, signal, fetchImpl = fetch }) {
  const payload = draftRevisionPayload(items);
  if (typeof draftId !== "string" || !draftId.trim()) {
    throw new DraftRevisionError("缺少可修改的报价编号，请重新试算", { code: 400 });
  }
  let response;
  try {
    response = await fetchImpl(`${baseUrl}/order-drafts/${encodeURIComponent(draftId)}/revisions`, {
      method: "POST", credentials: "same-origin", signal,
      headers: { "Content-Type": "application/json", Accept: "application/json",
        ...(token ? { "X-User-Token": token } : {}) },
      body: JSON.stringify(payload),
    });
  } catch (cause) {
    throw new DraftRevisionError("连接中断，重新报价的结果尚未确认，请勿重复提交", { uncertain: true, cause });
  }
  let body;
  try { body = await response.json(); }
  catch (cause) {
    throw new DraftRevisionError(response.ok ? "重新报价返回信息不完整，请重新核实报价" : "暂时无法完成重新报价，请核实结果",
      { status: response.status, uncertain: response.ok || response.status >= 500 || response.status === 408, cause });
  }
  if (!response.ok || body?.code !== 0) {
    const code = Number.isInteger(body?.code) ? body.code : null;
    throw new DraftRevisionError(responseMessage(body, "暂时无法完成重新报价，请核实结果"), {
      status: response.status, code,
      uncertain: response.status >= 500 || response.status === 408 || code >= 500
        || (response.ok && !Number.isInteger(body?.code)),
    });
  }
  const result = normalizeRevision(body.data, draftId, payload.orderItems);
  if (!result) {
    throw new DraftRevisionError("重新报价返回信息不完整，请重新核实报价", { status: response.status, code: 0, uncertain: true });
  }
  return result;
}
