import { hasReliableOrderReceipt, classifyConfirmationOutcome } from "./order-lookup.mjs?v=20261102-1";

export const MAX_SPEECH_TEXT_LENGTH = 1000;
const amount = (value) => (typeof value === "number" || typeof value === "string" && value.trim() !== "")
  && Number.isFinite(Number(value)) && Number(value) >= 0;
const money = (value) => `${String(value).replace(/(\.\d*?)0+$/, "$1").replace(/\.$/, "")}元`;
const validName = (value) => typeof value === "string" && value.trim();

/** Plain speech for an existing reply; never asks a model to invent another quote. */
export function cleanSpeechText(reply) {
  return String(reply ?? "")
    .replace(/```[\s\S]*?(?:```|$)/g, "")
    .split(/\r?\n/)
    .filter((line) => !/草稿(?:编号|号|ID)|有效期|(?:conversationId|ownerKey|draftId|expireAt)\s*[:：]/i.test(line))
    .filter((line) => !/^\s*\|?[\s:|\-]+\|\s*$/.test(line))
    .map((line) => line.replace(/^\s*(?:#{1,6}\s+|[-*>]\s+|\d+[.)、]\s+)/, "")
      .replace(/^\s*\||\|\s*$/g, "").replace(/\s*\|\s*/g, "，"))
    .join("\n")
    .replace(/!\[[^\]]*\]\([^)]*\)/g, "")
    .replace(/\[([^\]]+)\]\([^)]*\)/g, "$1")
    .replace(/https?:\/\/[^\s)）]+/g, "")
    .replace(/\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b/gi, "")
    .replace(/<[^>]*>/g, "")
    .replace(/[`*_~]/g, "")
    .replace(/[¥￥]\s*(\d+(?:\.\d+)?)/g, "$1元")
    .replace(/(\d+)\s*[×x]\s*(\d+)/g, "$1乘以$2")
    .replace(/[\p{Extended_Pictographic}\uFE0F\u200D]/gu, "")
    .replace(/[ \t]+/g, " ")
    .replace(/\n+/g, "。")
    .replace(/[。；]\s*[。；]+/g, "。")
    .trim();
}

function quoteSpeech(result) {
  const quote = result?.draft;
  if (!["SUCCESS", "BUDGET_EXCEEDED"].includes(result?.status) || !quote || !Array.isArray(quote.items)
      || !quote.items.length || !amount(quote.totalAmount) || typeof quote.overBudget !== "boolean"
      || !(quote.budget == null || amount(quote.budget))) return null;
  if (!quote.items.every((item) => validName(item?.dishName) && Number.isInteger(item.quantity)
      && item.quantity > 0 && amount(item.unitPrice) && amount(item.subTotal))) return null;
  const items = quote.items.map((item) => `${item.dishName}${item.quantity}份，小计${money(item.subTotal)}`).join("；");
  return cleanSpeechText(`这是草稿报价，尚未创建正式订单。${items}。合计${money(quote.totalAmount)}。`
    + (quote.budget == null ? "" : `预算${money(quote.budget)}，${quote.overBudget ? "已超出预算，不能确认下单" : "未超出预算"}。`)
    + (quote.overBudget ? "请先调整菜品或预算，再重新报价。" : "请核对菜品和数量，确认后再下单。"));
}

export function buildSpeechText({ reply, draftToolResult, orderToolResult, orderResultPending = false } = {}) {
  if (orderResultPending) return "本次订单结果仍待核实，请点击核实订单结果。确认结果之前，请不要重复下单。";
  if (classifyConfirmationOutcome(orderToolResult) === "rejected") {
    return cleanSpeechText(`本次未能下单。${orderToolResult.message}`);
  }
  if (orderToolResult?.status === "SUCCESS" && hasReliableOrderReceipt(orderToolResult.order)) {
    const order = orderToolResult.order;
    const names = { pending: "待处理", paid: "已支付", processing: "处理中", completed: "已完成", cancelled: "已取消" };
    return cleanSpeechText(`这份正式订单的状态是${names[order.orderStatus] || order.orderStatus}。`
      + order.items.map((item) => `${item.dishName}${item.quantity}份，单价${money(item.priceAtTime)}`).join("；")
      + `。订单金额${money(order.orderTotalMoney)}。`);
  }
  // An order rejection must keep its failure explanation, even if a quote is also present.
  if (!orderToolResult) {
    const quote = quoteSpeech(draftToolResult);
    if (quote) return quote;
  }
  return cleanSpeechText(reply);
}
