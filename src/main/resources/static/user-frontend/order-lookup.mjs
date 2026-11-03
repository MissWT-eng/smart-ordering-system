/** Read-only recovery for one captured confirmation ID. Never submits or retries an order. */
export class OrderLookupError extends Error {
  constructor(message, { status = 0, code = null, cause } = {}) {
    super(message, cause === undefined ? undefined : { cause });
    this.name = "OrderLookupError";
    this.status = status;
    this.code = code;
  }
}

const validId = (value) => (typeof value === "number" && Number.isSafeInteger(value) && value > 0)
  || (typeof value === "string" && /^[1-9]\d*$/.test(value));
const validAmount = (value) => (typeof value === "number" || (typeof value === "string" && value.trim() !== ""))
  && Number.isFinite(Number(value)) && Number(value) >= 0;

export function hasReliableOrderReceipt(order) {
  return !!order && validId(order.orderNum)
    && typeof order.orderTime === "string" && Number.isFinite(Date.parse(order.orderTime))
    && ["pending", "paid", "cancelled"].includes(order.orderStatus)
    && validAmount(order.orderTotalMoney)
    && Array.isArray(order.items) && order.items.length > 0
    && order.items.every((item) => item && validId(item.dishNumber)
      && typeof item.dishName === "string" && !!item.dishName.trim()
      && Number.isSafeInteger(item.quantity) && item.quantity > 0
      && validAmount(item.priceAtTime));
}

const rejectedConfirmationStatuses = new Set([
  "NO_MATCH", "CONFIRMATION_REQUIRED", "FORBIDDEN", "CONFIRMATION_MISMATCH",
  "BUDGET_EXCEEDED", "DISH_NOT_AVAILABLE", "INVALID_ARGUMENTS",
]);

export function classifyConfirmationOutcome(orderToolResult) {
  if (orderToolResult?.status === "SUCCESS" && hasReliableOrderReceipt(orderToolResult.order)) return "found";
  if (rejectedConfirmationStatuses.has(orderToolResult?.status) && orderToolResult.order == null
      && typeof orderToolResult.message === "string" && !!orderToolResult.message.trim()) return "rejected";
  // A completed chat or a success claim without a typed receipt says nothing reliable about the write.
  return "unknown";
}

export function shouldOfferOrderLookup({ confirmedDraftId, receipt = null, rejected = false } = {}) {
  return typeof confirmedDraftId === "string" && !!confirmedDraftId.trim()
    && !hasReliableOrderReceipt(receipt) && !rejected;
}

function safeMessage(value, fallback) {
  return typeof value === "string" && value.trim() && value.length <= 500 && !/[<>]/.test(value)
    ? value : fallback;
}

export async function lookupDraftOrder({ baseUrl = "", confirmedDraftId, token, signal, fetchImpl = fetch }) {
  if (typeof confirmedDraftId !== "string" || !confirmedDraftId.trim()) {
    throw new OrderLookupError("缺少本次确认的报价编号，无法核实订单结果", { code: 400 });
  }
  let response;
  try {
    response = await fetchImpl(`${baseUrl}/order-drafts/${encodeURIComponent(confirmedDraftId)}/order`, {
      method: "GET", credentials: "same-origin", cache: "no-store", signal,
      headers: { Accept: "application/json", ...(token ? { "X-User-Token": token } : {}) },
    });
  } catch (cause) {
    if (cause?.name === "AbortError" || signal?.aborted) throw cause;
    throw new OrderLookupError("查询连接中断，订单结果尚未核实，请稍后再次核实", { cause });
  }
  let body;
  try { body = await response.json(); }
  catch (cause) {
    if (cause?.name === "AbortError" || signal?.aborted) throw cause;
    throw new OrderLookupError("查询返回信息异常，订单结果尚未核实", { status: response.status, cause });
  }
  if (!response.ok || body?.code !== 0) {
    throw new OrderLookupError(safeMessage(body?.msg, "查询未完成，订单结果尚未核实"), {
      status: response.status, code: Number.isInteger(body?.code) ? body.code : null,
    });
  }
  const data = body.data;
  if (data?.status === "found" && hasReliableOrderReceipt(data.order)) {
    return { status: "found", order: data.order, message: safeMessage(data.message, "已查询到订单") };
  }
  if (data?.status === "not_found" && data.order === null) {
    return { status: "not_found", order: null,
      message: safeMessage(data.message, "暂未查询到，请稍后再次核实") };
  }
  throw new OrderLookupError("查询返回的订单结果不完整，订单结果尚未核实", { status: response.status, code: 0 });
}
