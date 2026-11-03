import test from "node:test";
import assert from "node:assert/strict";
import { OrderLookupError, hasReliableOrderReceipt, classifyConfirmationOutcome, lookupDraftOrder, shouldOfferOrderLookup } from "../../main/resources/static/user-frontend/order-lookup.mjs";

function receipt() {
  return { orderNum: 12345, orderTime: "2026-10-27T10:20:30", orderStatus: "pending",
    orderTotalMoney: "42.30", items: [{ dishNumber: 7, dishName: "清炒时蔬", quantity: 3, priceAtTime: "14.10" }] };
}
const found = (order = receipt()) => Response.json({ code: 0, data: { status: "found", order, message: "已查询到订单" } });

test("lookup only GETs the captured confirmed ID with guest cookies, no body or confirmation", async () => {
  let calls = 0;
  const result = await lookupDraftOrder({ confirmedDraftId: "confirmed/old?", currentDraftId: "must-not-use", fetchImpl: async (url, init) => {
    calls++;
    assert.equal(url, "/order-drafts/confirmed%2Fold%3F/order");
    assert.equal(init.method, "GET");
    assert.equal(init.credentials, "same-origin");
    assert.equal(init.cache, "no-store");
    assert.equal(init.body, undefined);
    assert.equal(init.headers["X-User-Token"], undefined);
    return found();
  } });
  assert.equal(calls, 1);
  assert.equal(result.status, "found");
  assert.equal(result.order.orderTotalMoney, "42.30");
});

test("registered lookup forwards the captured token and abort signal", async () => {
  const controller = new AbortController();
  await lookupDraftOrder({ baseUrl: "/api", confirmedDraftId: "old-confirmed", token: "captured-user-token", signal: controller.signal,
    fetchImpl: async (url, init) => {
      assert.equal(url, "/api/order-drafts/old-confirmed/order");
      assert.equal(init.headers["X-User-Token"], "captured-user-token");
      assert.equal(init.signal, controller.signal);
      return found();
    } });
});

test("explicit not_found remains pending rather than becoming a fake receipt", async () => {
  const result = await lookupDraftOrder({ confirmedDraftId: "confirmed", fetchImpl: async () => Response.json({ code: 0, data: { status: "not_found", order: null, message: "暂未查询到订单，提交可能仍在处理中，请稍后核实" } }) });
  assert.equal(result.status, "not_found");
  assert.equal(result.order, null);
  assert.match(result.message, /稍后核实/);
});

test("only complete, typed receipts are reliable, including cancelled existing orders", () => {
  assert.equal(hasReliableOrderReceipt(receipt()), true);
  const cancelled = { ...receipt(), orderStatus: "cancelled", orderNum: "12345" };
  assert.equal(hasReliableOrderReceipt(cancelled), true);
  for (const mutation of [o => o.orderNum = null, o => o.orderNum = Number.MAX_SAFE_INTEGER + 1,
    o => o.orderTime = "bad", o => o.orderStatus = "unrecognized", o => o.orderTotalMoney = null,
    o => o.orderTotalMoney = -1, o => o.items = [], o => o.items[0].dishNumber = null,
    o => o.items[0].dishName = "", o => o.items[0].quantity = 0, o => o.items[0].priceAtTime = "NaN"]) {
    const order = receipt(); mutation(order);
    assert.equal(hasReliableOrderReceipt(order), false);
  }
});

test("malformed success is an error and never treated as not_found", async () => {
  for (const data of [null, {}, { status: "FOUND", order: receipt() }, { status: "found", order: null },
    { status: "found", order: { orderNum: 1 } }, { status: "not_found", order: receipt() }, { status: "not_found" }]) {
    await assert.rejects(lookupDraftOrder({ confirmedDraftId: "confirmed", fetchImpl: async () => Response.json({ code: 0, data }) }),
      error => error instanceof OrderLookupError && error.status === 200 && /尚未核实/.test(error.message));
  }
});

test("business and HTTP query errors preserve status/code and never retry", async () => {
  for (const status of [401, 403, 404, 409, 500]) {
    let calls = 0;
    await assert.rejects(lookupDraftOrder({ confirmedDraftId: "confirmed", fetchImpl: async () => {
      calls++; return Response.json({ code: status, msg: "查询未完成" }, { status });
    } }), error => error.status === status && error.code === status && error.message === "查询未完成");
    assert.equal(calls, 1);
  }
});

test("network and non-JSON failures leave the result unknown without exposing raw content", async () => {
  for (const fetcher of [async () => { throw new Error("network details"); }, async () => new Response("<html>proxy secret</html>", { status: 502 }), async () => new Response("broken JSON")]) {
    let calls = 0;
    await assert.rejects(lookupDraftOrder({ confirmedDraftId: "confirmed", fetchImpl: async (...args) => { calls++; return fetcher(...args); } }),
      error => /尚未核实/.test(error.message) && !/network details|proxy secret|broken JSON|<html>/.test(error.message));
    assert.equal(calls, 1);
  }
});

test("abort propagates unchanged during fetch and response parsing", async () => {
  const abort = new DOMException("aborted", "AbortError");
  for (const fetcher of [async () => { throw abort; }, async () => ({ status: 200, ok: true, json: async () => { throw abort; } })]) {
    await assert.rejects(lookupDraftOrder({ confirmedDraftId: "confirmed", fetchImpl: fetcher }), error => error === abort);
  }
});

test("invalid captured IDs never make a request, regardless of a current draft", async () => {
  for (const confirmedDraftId of [undefined, null, "", " "]) {
    let calls = 0;
    await assert.rejects(lookupDraftOrder({ confirmedDraftId, currentDraftId: "not-a-confirmation", fetchImpl: async () => { calls++; return found(); } }),
      error => error.code === 400);
    assert.equal(calls, 0);
  }
});

test("lookup affordance excludes ordinary chat, reliable receipts and explicit rejection", () => {
  assert.equal(shouldOfferOrderLookup(), false);
  assert.equal(shouldOfferOrderLookup({ currentDraftId: "not-submitted" }), false);
  assert.equal(shouldOfferOrderLookup({ confirmedDraftId: "confirmed" }), true);
  assert.equal(shouldOfferOrderLookup({ confirmedDraftId: "confirmed", completed: true }), true);
  assert.equal(shouldOfferOrderLookup({ confirmedDraftId: "confirmed", receipt: { orderNum: 1 } }), true);
  assert.equal(shouldOfferOrderLookup({ confirmedDraftId: "confirmed", receipt: receipt() }), false);
  assert.equal(shouldOfferOrderLookup({ confirmedDraftId: "confirmed", rejected: true }), false);
});

test("confirmation classification only trusts a complete SUCCESS receipt", () => {
  assert.equal(classifyConfirmationOutcome({ status: "SUCCESS", order: receipt() }), "found");
  for (const orderToolResult of [undefined, null, {}, { reply: "订单已经创建" },
    { status: "SUCCESS", order: null }, { status: "SUCCESS", order: { orderNum: 12345 } },
    { status: "SUCCESS", message: "下单成功" }, { status: "UNKNOWN", order: receipt() },
    { status: "PENDING", message: "仍在处理" }]) {
    assert.equal(classifyConfirmationOutcome(orderToolResult), "unknown");
  }
});

test("only known, explicit, non-contradictory tool rejections resolve a confirmation", () => {
  for (const status of ["NO_MATCH", "CONFIRMATION_REQUIRED", "FORBIDDEN", "CONFIRMATION_MISMATCH",
    "BUDGET_EXCEEDED", "DISH_NOT_AVAILABLE", "INVALID_ARGUMENTS"]) {
    assert.equal(classifyConfirmationOutcome({ status, order: null, message: "本次确认被明确拒绝" }), "rejected");
    assert.equal(classifyConfirmationOutcome({ status, message: "本次确认被明确拒绝" }), "rejected");
    assert.equal(classifyConfirmationOutcome({ status, order: receipt(), message: "本次确认被明确拒绝" }), "unknown");
    assert.equal(classifyConfirmationOutcome({ status, order: { orderNum: 1 }, message: "本次确认被明确拒绝" }), "unknown");
    assert.equal(classifyConfirmationOutcome({ status, message: " " }), "unknown");
    assert.equal(classifyConfirmationOutcome({ status }), "unknown");
  }
});

test("a done confirmation without a reliable outcome keeps the original captured lookup ID", () => {
  for (const result of [null, { status: "SUCCESS", order: {} }, { status: "UNRECOGNIZED", message: "成功" }]) {
    const outcome = classifyConfirmationOutcome(result);
    assert.equal(shouldOfferOrderLookup({ confirmedDraftId: "original-confirmed", completed: true,
      currentDraftId: "later-current", receipt: outcome === "found" ? result.order : null,
      rejected: outcome === "rejected" }), true);
  }
});
