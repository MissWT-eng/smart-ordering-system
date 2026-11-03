// Optional browser regression. Requires Playwright and an installed Chromium/Edge executable.
// Serves the real frontend locally; all business requests are isolated test responses.
import assert from "node:assert/strict";
import test from "node:test";
import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";

const { chromium } = createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE || "playwright");
const root = new URL("../../main/resources/static/user-frontend/", import.meta.url);
const mime = { html: "text/html", css: "text/css", js: "text/javascript", mjs: "text/javascript" };
const server = createServer(async (request, response) => {
  const path = new URL(request.url, "http://localhost").pathname;
  const name = path.replace(/^\/user-frontend\//, "");
  if (!path.startsWith("/user-frontend/") || !/^[a-z0-9-]+\.(html|css|js|mjs)$/.test(name)) {
    response.writeHead(404).end(); return;
  }
  try {
    const data = await readFile(fileURLToPath(new URL(name, root)));
    response.writeHead(200, { "Content-Type": mime[name.split(".").at(-1)], "Cache-Control": "no-store" }).end(data);
  } catch { response.writeHead(404).end(); }
});
await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
const origin = `http://127.0.0.1:${server.address().port}`;
const browser = await chromium.launch({ headless: true,
  ...(process.env.PLAYWRIGHT_EXECUTABLE_PATH ? { executablePath: process.env.PLAYWRIGHT_EXECUTABLE_PATH } : {}) });
const frame = (event, data) => `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`;
const meta = frame("meta", { conversationId: "recovery-test" });
const quote = (id = "submitted-a") => ({ status: "SUCCESS", draftId: id, expireAt: "2099-10-27T12:00:00",
  draft: { items: [{ dishNumber: 7, dishName: "清炒时蔬", quantity: 2, unitPrice: 14, subTotal: 28 }],
    totalAmount: 28, budget: 100, overBudget: false } });
const receipt = () => ({ orderNum: 12345, orderTime: "2026-10-27T12:00:00", orderStatus: "pending",
  orderTotalMoney: 28, items: [{ dishNumber: 7, dishName: "清炒时蔬", quantity: 2, priceAtTime: 14 }] });

async function waitForResponsiveLayout(page, { width, height }) {
  await page.waitForFunction(({ width, height }) => {
    const navWidth = width <= 760 ? width : parseFloat(getComputedStyle(document.documentElement).getPropertyValue("--sidebar-width"));
    return innerWidth === width && innerHeight === height
      && Math.abs(document.querySelector(".nav").getBoundingClientRect().width - navWidth) < 1
      && Math.abs(document.querySelector(".agent-container").getBoundingClientRect().width - (width <= 760 ? width : width - navWidth)) < 1;
  }, { width, height });
  await page.evaluate(async () => {
    await document.fonts.ready;
    await new Promise((resolve) => {
      let previous = "", stableFrames = 0;
      const sample = () => {
        const elements = [document.documentElement, document.body, document.querySelector(".nav"),
          document.querySelector(".chat-timeline"), document.querySelector(".chat-composer"),
          ...document.querySelectorAll(".order-recovery")];
        const current = JSON.stringify(elements.map((element) => {
          const rect = element.getBoundingClientRect();
          return [rect.x, rect.y, rect.width, rect.height, element.scrollWidth, element.clientWidth];
        }));
        stableFrames = current === previous ? stableFrames + 1 : 0;
        previous = current;
        if (stableFrames >= 3) resolve();
        else requestAnimationFrame(sample);
      };
      requestAnimationFrame(sample);
    });
  });
}

async function fixture({ loggedIn = false, timeout = false, lookupTimeout = false } = {}) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 }, reducedMotion: "reduce" });
  await context.addCookies([{ name: "JSESSIONID", value: "isolated-recovery-session", url: origin }]);
  if (loggedIn) await context.addInitScript(() => {
    if (!localStorage.getItem("ordering_user")) localStorage.setItem("ordering_user", JSON.stringify({ userId: 41, token: "fixture-token" }));
  });
  if (timeout) await context.addInitScript(() => {
    const original = window.setTimeout;
    window.setTimeout = (callback, delay, ...args) => original(callback, delay === 75_000 ? 80 : delay, ...args);
  });
  if (lookupTimeout) await context.addInitScript(() => {
    const original = window.setTimeout;
    window.setTimeout = (callback, delay, ...args) => original(callback, delay === 20_000 ? 80 : delay, ...args);
  });
  const page = await context.newPage();
  const state = { confirmation: "lost", lookup: "found", reply: "quote", current: "submitted-a", loginUserId: 41,
    profile: "success", orders: "success", requests: [], protectedRequests: [], releases: [], errors: [] };
  page.on("pageerror", (error) => state.errors.push(error.message));
  await page.route("**/*", async (route) => {
    const request = route.request(), path = new URL(request.url()).pathname;
    if (path === "/agent/chat/stream") {
      const body = request.postDataJSON();
      state.requests.push({ method: request.method(), path, body, headers: request.headers() });
      if (!body.confirmedDraftId) {
        if (state.reply === "lost") return route.fulfill({ contentType: "text/event-stream", body: meta + frame("delta", { text: "聊天片段" }) });
        return route.fulfill({ contentType: "text/event-stream", body: meta + frame("done", {
          conversationId: "recovery-test", reply: "请核对报价。", ...(state.reply === "quote" ? { draftToolResult: quote(state.current) } : {}),
        }) });
      }
      if (state.confirmation === "refused") return route.fulfill({ status: 400, json: { code: 400, msg: "确认请求被拒绝" } });
      if (state.confirmation === "unauthorized") return route.fulfill({ status: 401, json: { code: 401, msg: "登录凭证已过期，本次请求未执行" } });
      if (state.confirmation === "gateway") return route.fulfill({ status: 504, body: "gateway timeout" });
      if (state.confirmation === "http-timeout") return route.fulfill({ status: 408, body: "request timed out" });
      if (state.confirmation === "hang") {
        await new Promise((resolve) => { state.releases.push(resolve); });
        return route.abort().catch(() => {});
      }
      if (state.confirmation === "lost") return route.fulfill({ contentType: "text/event-stream", body: meta + frame("delta", { text: "正在提交订单……" }) });
      let orderToolResult = { status: "SUCCESS", order: receipt() };
      if (["empty-done", "text-success"].includes(state.confirmation)) orderToolResult = null;
      if (state.confirmation === "malformed-success") orderToolResult = { status: "SUCCESS", order: { orderNum: 12345 } };
      if (state.confirmation === "unknown-status") orderToolResult = { status: "ERROR", order: null, message: "未能返回明确结果" };
      if (state.confirmation === "contradictory-refusal") orderToolResult = { status: "INVALID_ARGUMENTS", message: "拒绝", order: receipt() };
      if (state.confirmation === "tool-refused") orderToolResult = { status: "INVALID_ARGUMENTS", message: "草稿已失效" };
      const bodyText = meta + frame("done", { conversationId: "recovery-test",
        reply: state.confirmation === "text-success" ? "订单已经创建成功。" : "本次请求处理完成。", orderToolResult });
      return route.fulfill({ contentType: "text/event-stream", body: bodyText + (state.confirmation === "receipt-then-error" ? frame("error", { code: 504, msg: "尾部断开" }) : "") });
    }
    if (/^\/order-drafts\/[^/]+\/order$/.test(path)) {
      state.requests.push({ method: request.method(), path, body: request.postData(), headers: request.headers() });
      if (state.lookup === "pending") await new Promise((resolve) => { state.releases.push(resolve); });
      if (state.lookup === "network") return route.abort();
      if (state.lookup === "error") return route.fulfill({ status: 500, json: { code: 500, msg: "查询异常" } });
      if (state.lookup === "expired") return route.fulfill({ status: 401, json: { code: 401, msg: "登录凭证已过期" } });
      if (state.lookup === "invalid") return route.fulfill({ json: { code: 0, data: { status: "found", order: { orderNum: 1 } } } });
      return route.fulfill({ json: { code: 0, data: state.lookup === "not_found" ? { status: "not_found", order: null }
        : { status: "found", order: receipt() } } }).catch(() => {});
    }
    if (path === "/users/identify") {
      state.requests.push({ method: request.method(), path, body: request.postDataJSON() });
      return route.fulfill({ json: { code: 0, data: { userId: state.loginUserId, token: "renewed-user-token", phoneNumber: "13800000000" } } });
    }
    if (path === "/users/me/preference" || /^\/orders\/\d+$/.test(path) || /^\/users\/\d+$/.test(path)) {
      const headers = request.headers();
      state.protectedRequests.push({ method: request.method(), path, headers });
      const expired = (path === "/users/me/preference" ? state.profile : state.orders) === "expired"
        && headers["x-user-token"] === "fixture-token";
      if (expired) return route.fulfill({ status: 401, json: { code: 401, msg: "登录凭证已过期" } });
      return route.fulfill({ json: { code: 0, data: path.startsWith("/orders/") ? { list: [] } : null } });
    }
    if (!path.startsWith("/user-frontend/")) return route.abort();
    return route.continue();
  });
  await page.goto(`${origin}/user-frontend/index.html`);
  await page.waitForFunction(() => !!window.OrderingUI);
  const send = async (text) => {
    await page.locator("#chatInput").fill(text);
    await page.locator("#sendChat").click();
    await page.waitForFunction(() => !document.querySelector("#newConversation").disabled);
  };
  const confirm = async () => {
    await send("请试算");
    await page.locator(".quote-actions .btn-primary").click();
    await page.locator("#submitAgentOrder").click();
  };
  const confirmationCount = () => state.requests.filter((request) => request.body?.confirmedDraftId).length;
  return { page, state, send, confirm, confirmationCount,
    close: async () => { state.releases.splice(0).forEach((resolve) => resolve()); await context.close(); assert.deepEqual(state.errors, []); } };
}

try {
  await test("committed order with a lost success reply is recovered by one GET without another confirmation", async () => {
    const f = await fixture();
    try {
      await f.confirm();
      await f.page.locator(".order-lookup-button").waitFor();
      assert.equal(f.confirmationCount(), 1);
      assert.equal(f.state.requests.filter((r) => r.method === "GET").length, 0);
      assert(await f.page.locator(".quote-actions .btn-primary").isDisabled());
      for (const [width, height] of [[1440, 900], [1024, 768], [390, 844], [320, 640]]) {
        await f.page.setViewportSize({ width, height });
        await f.page.locator(".recovery-details").evaluate((details) => { details.open = true; });
        await waitForResponsiveLayout(f.page, { width, height });
        assert(await f.page.evaluate(() => document.documentElement.scrollHeight === innerHeight
          && document.querySelector(".chat-composer").getBoundingClientRect().bottom <= innerHeight + 1
          && document.documentElement.scrollWidth <= innerWidth
          && [...document.querySelectorAll(".order-recovery")].every((element) => element.scrollWidth <= element.clientWidth + 1)));
      }
      await f.page.setViewportSize({ width: 1280, height: 900 });
      await waitForResponsiveLayout(f.page, { width: 1280, height: 900 });
      await f.page.locator(".order-lookup-button").click();
      await f.page.locator(".is-receipt").waitFor();
      const lookup = f.state.requests.find((r) => r.method === "GET");
      assert.equal(lookup.path, "/order-drafts/submitted-a/order");
      assert.equal(lookup.body, null);
      assert.match(lookup.headers.cookie, /JSESSIONID=isolated-recovery-session/);
      assert.equal(f.confirmationCount(), 1);
      assert.equal(await f.page.locator(".order-lookup-button").count(), 0);
      assert.match(await f.page.locator(".is-receipt").innerText(), /12345/);
    } finally { await f.close(); }
  });
  await test("not_found and query failures retain recovery; a later found response resolves it", async () => {
    const f = await fixture();
    try {
      await f.confirm(); await f.page.locator(".order-lookup-button").waitFor();
      for (const mode of ["not_found", "network", "error", "invalid"]) {
        f.state.lookup = mode;
        await f.page.locator(".order-lookup-button").click();
        await f.page.waitForFunction(() => !document.querySelector(".order-lookup-button").disabled);
        assert.match(await f.page.locator(".recovery-feedback").innerText(), mode === "not_found" ? /暂未查询到，请稍后再次核实/ : /查询未完成/);
        assert.equal(await f.page.locator(".is-receipt").count(), 0);
      }
      f.state.lookup = "found";
      await f.page.locator(".order-lookup-button").click(); await f.page.locator(".is-receipt").waitFor();
      assert.equal(f.confirmationCount(), 1);
    } finally { await f.close(); }
  });
  await test("ordinary interrupted chat has no order recovery button", async () => {
    const f = await fixture();
    try { f.state.reply = "lost"; await f.send("聊聊午饭"); assert.equal(await f.page.locator(".order-lookup-button").count(), 0); }
    finally { await f.close(); }
  });
  for (const mode of ["empty-done", "text-success", "malformed-success", "unknown-status", "contradictory-refusal"]) {
    await test(`done with no reliable order outcome (${mode}) keeps recovery and blocks another confirmation`, async () => {
      const f = await fixture();
      try {
        f.state.confirmation = mode; await f.confirm();
        await f.page.locator(".order-lookup-button").waitFor();
        await f.page.waitForFunction(() => !document.querySelector("#newConversation").disabled);
        assert.match(await f.page.locator(".message-state").last().innerText(), /未收到可靠订单回执/);
        assert(await f.page.locator(".quote-actions .btn-primary").isDisabled());
        assert.equal(await f.page.locator(".is-receipt").count(), 0);
        await f.page.locator(".order-lookup-button").click(); await f.page.locator(".is-receipt").waitFor();
        assert.equal(f.state.requests.find((r) => r.method === "GET").path, "/order-drafts/submitted-a/order");
        assert.equal(f.confirmationCount(), 1);
      } finally { await f.close(); }
    });
  }
  for (const sameUser of [true, false]) {
    await test(`expired lookup offers re-login and ${sameUser ? "resumes the same user's original query" : "drops the old user's recovery after account switch"}`, async () => {
      const f = await fixture({ loggedIn: true });
      try {
        await f.confirm(); await f.page.locator(".order-lookup-button").waitFor(); f.state.lookup = "expired";
        await f.page.locator(".order-lookup-button").click();
        await f.page.getByRole("button", { name: "重新登录后核实订单", exact: true }).waitFor();
        assert.match(await f.page.locator(".recovery-feedback").innerText(), /登录凭证已过期.*仍待核实/);
        assert.equal(await f.page.locator(".is-receipt").count(), 0);
        assert.equal(f.state.requests.filter((r) => r.method === "GET").length, 1);
        await f.page.getByRole("button", { name: "重新登录后核实订单", exact: true }).click();
        await f.page.locator("#loginModal").waitFor({ state: "visible" });
        assert.equal(f.state.requests.filter((r) => r.method === "GET").length, 1);
        f.state.lookup = "found"; f.state.loginUserId = sameUser ? 41 : 42;
        await f.page.locator("#loginPhone").fill("13800000000"); await f.page.locator("#loginSubmit").click();
        await f.page.locator("#loginModal").waitFor({ state: "hidden" });
        if (sameUser) {
          await f.page.locator(".is-receipt").waitFor();
          const gets = f.state.requests.filter((r) => r.method === "GET");
          assert.equal(gets.length, 2);
          assert.equal(gets[1].path, "/order-drafts/submitted-a/order");
          assert.equal(gets[1].headers["x-user-token"], "renewed-user-token");
          assert.equal(await f.page.locator(".order-lookup-button").count(), 0);
        } else {
          await f.page.waitForFunction(() => document.querySelector("#agentStatus").textContent === "点餐助手已就绪");
          assert.equal(await f.page.locator(".order-recovery,.is-receipt").count(), 0);
          assert.equal(f.state.requests.filter((r) => r.method === "GET").length, 1);
        }
        assert.equal(f.confirmationCount(), 1);
      } finally { await f.close(); }
    });
  }
  await test("expired recovery survives preference refresh, protected-page expiry and the account login entrance", async () => {
    const f = await fixture({ loggedIn: true });
    try {
      await f.confirm(); await f.page.locator(".order-lookup-button").waitFor(); f.state.lookup = "expired";
      await f.page.locator(".order-lookup-button").click();
      await f.page.getByRole("button", { name: "重新登录后核实订单", exact: true }).waitFor();
      f.state.profile = f.state.orders = "expired";
      const preferenceCount = f.state.protectedRequests.filter((r) => r.path === "/users/me/preference").length;
      await f.page.locator("#refreshTaste").click();
      await f.page.getByRole("button", { name: "重新登录并核实订单", exact: true }).waitFor();
      assert.equal(f.state.protectedRequests.filter((r) => r.path === "/users/me/preference").length, preferenceCount);
      assert.equal(await f.page.locator(".order-recovery").count(), 1);
      await f.page.locator('.nav-btn[data-page="orders"]').click();
      await f.page.locator("#loginModal").waitFor({ state: "visible" });
      assert(f.state.protectedRequests.some((r) => r.path === "/orders/41" && r.headers["x-user-token"] === "fixture-token"));
      assert.equal(await f.page.evaluate(() => window.OrderingUI.getUser()?.userId), 41);
      assert.equal(await f.page.locator(".order-recovery").count(), 1);
      await f.page.locator('[data-close="loginModal"]').click();
      await f.page.locator('.nav-btn[data-page="agent"]').click();
      await f.page.getByRole("button", { name: "重新登录后核实订单", exact: true }).waitFor();
      assert.equal(f.state.protectedRequests.filter((r) => r.path === "/users/me/preference").length, preferenceCount);
      await f.page.locator("#navLoginBtn").click();
      await f.page.locator("#loginModal").waitFor({ state: "visible" });
      assert(await f.page.locator("#userModal").isHidden());
      assert(!f.state.protectedRequests.some((r) => /^\/users\/\d+$/.test(r.path)));
      await f.page.locator('[data-close="loginModal"]').click();
      await f.page.setViewportSize({ width: 390, height: 844 });
      await waitForResponsiveLayout(f.page, { width: 390, height: 844 });
      await f.page.locator("#openDiningPanel").click();
      await f.page.getByRole("button", { name: "重新登录并核实订单", exact: true }).click();
      assert.equal(await f.page.locator("#diningPanelDialog").evaluate((dialog) => dialog.open), false);
      f.state.lookup = "found";
      await f.page.locator("#loginPhone").fill("13800000000");
      assert.equal(await f.page.locator("#loginPhone").inputValue(), "13800000000");
      await f.page.locator("#loginSubmit").click();
      await f.page.locator(".is-receipt").waitFor();
      const gets = f.state.requests.filter((r) => r.method === "GET");
      assert.equal(gets.length, 2);
      assert.equal(gets[1].path, "/order-drafts/submitted-a/order");
      assert.equal(gets[1].headers["x-user-token"], "renewed-user-token");
      assert.equal(f.confirmationCount(), 1);
    } finally { await f.close(); }
  });
  await test("another protected request expiring during confirmation preserves its captured ID for re-login recovery", async () => {
    const f = await fixture({ loggedIn: true });
    try {
      f.state.confirmation = "hang"; await f.confirm();
      await f.page.waitForFunction(() => !document.querySelector("#stopChat").classList.contains("hidden"));
      f.state.profile = "expired";
      await f.page.evaluate(() => window.OrderingUI.api("/users/me/preference", {
        headers: { "X-User-Token": window.OrderingUI.getUser().token },
      }).catch(() => {}));
      await f.page.locator("#loginModal").waitFor({ state: "visible" });
      await f.page.waitForFunction(() => !document.querySelector("#newConversation").disabled);
      assert.equal(await f.page.evaluate(() => window.OrderingUI.getUser()?.userId), 41);
      assert.equal(await f.page.locator(".order-recovery").count(), 1);
      assert.match(await f.page.locator(".recovery-details").textContent(), /submitted-a/);
      assert.equal(f.state.requests.filter((r) => r.method === "GET").length, 0);
      assert.equal(f.confirmationCount(), 1);
      await f.page.locator("#loginPhone").fill("13800000000"); await f.page.locator("#loginSubmit").click();
      await f.page.locator(".is-receipt").waitFor();
      const gets = f.state.requests.filter((r) => r.method === "GET");
      assert.equal(gets.length, 1);
      assert.equal(gets[0].path, "/order-drafts/submitted-a/order");
      assert.equal(gets[0].headers["x-user-token"], "renewed-user-token");
      assert.equal(f.confirmationCount(), 1);
    } finally { await f.close(); }
  });
  await test("an explicit preflight 401 rejection clears login without creating an uncertain order recovery", async () => {
    const f = await fixture({ loggedIn: true });
    try {
      f.state.confirmation = "unauthorized"; await f.confirm();
      await f.page.locator("#loginModal").waitFor({ state: "visible" });
      assert.equal(await f.page.evaluate(() => window.OrderingUI.getUser()), null);
      assert.equal(await f.page.evaluate(() => localStorage.getItem("ordering_user")), null);
      assert.equal(await f.page.locator(".order-recovery,.is-receipt").count(), 0);
      assert.equal(f.state.requests.filter((r) => r.method === "GET").length, 0);
      assert.equal(f.confirmationCount(), 1);
    } finally { await f.close(); }
  });
  await test("a late 401 for a replaced same-user token keeps recovery available without another login", async () => {
    const f = await fixture({ loggedIn: true });
    try {
      await f.confirm(); await f.page.locator(".order-lookup-button").waitFor(); f.state.lookup = "pending";
      await f.page.locator(".order-lookup-button").click();
      await f.page.waitForFunction(() => document.querySelector(".order-recovery").getAttribute("aria-busy") === "true");
      await f.page.evaluate(() => {
        localStorage.setItem("ordering_user", JSON.stringify({ userId: 41, token: "rotated-user-token" }));
        window.dispatchEvent(new StorageEvent("storage", { key: "ordering_user" }));
      });
      f.state.lookup = "expired"; f.state.releases.splice(0).forEach((resolve) => resolve());
      await f.page.waitForFunction(() => !document.querySelector(".order-lookup-button").disabled);
      assert.match(await f.page.locator(".recovery-feedback").innerText(), /登录凭证已更新.*再次核实/);
      assert.equal(await f.page.locator(".order-lookup-button").innerText(), "再次核实订单结果");
      assert(await f.page.locator("#loginModal").isHidden());
      f.state.reply = "plain"; await f.send("继续聊");
      f.state.lookup = "found";
      await f.page.locator(".order-lookup-button").click(); await f.page.locator(".is-receipt").waitFor();
      const gets = f.state.requests.filter((r) => r.method === "GET");
      assert.equal(gets.length, 2);
      assert.equal(gets[0].headers["x-user-token"], "fixture-token");
      assert.equal(gets[1].headers["x-user-token"], "rotated-user-token");
      assert.equal(gets[1].path, "/order-drafts/submitted-a/order");
      assert.equal(f.confirmationCount(), 1);
    } finally { await f.close(); }
  });
  await test("a lookup timeout leaves the original confirmation pending and permits another GET", async () => {
    const f = await fixture({ lookupTimeout: true });
    try {
      await f.confirm(); await f.page.locator(".order-lookup-button").waitFor(); f.state.lookup = "pending";
      await f.page.locator(".order-lookup-button").click();
      await f.page.waitForFunction(() => !document.querySelector(".order-lookup-button").disabled);
      assert.match(await f.page.locator(".recovery-feedback").innerText(), /查询超时，核实未完成/);
      assert.equal(await f.page.locator(".is-receipt").count(), 0);
      assert.equal(f.confirmationCount(), 1);
      f.state.lookup = "found"; f.state.releases.splice(0).forEach((resolve) => resolve());
      await f.page.locator(".order-lookup-button").click(); await f.page.locator(".is-receipt").waitFor();
      assert.equal(f.confirmationCount(), 1);
      assert.equal(f.state.requests.filter((r) => r.method === "GET").length, 2);
    } finally { await f.close(); }
  });
  for (const mode of ["refused", "tool-refused", "success", "receipt-then-error"]) {
    await test(`reliable completion or explicit refusal (${mode}) has no recovery button`, async () => {
      const f = await fixture();
      try {
        f.state.confirmation = mode; await f.confirm();
        await f.page.waitForFunction(() => !document.querySelector("#newConversation").disabled);
        assert.equal(await f.page.locator(".order-lookup-button").count(), 0);
        assert.equal(f.confirmationCount(), 1);
      } finally { await f.close(); }
    });
  }
  for (const mode of ["gateway", "http-timeout", "timeout", "stop"]) {
    await test(`uncertain confirmation (${mode}) offers a lookup`, async () => {
      const f = await fixture({ timeout: mode === "timeout" });
      try {
        f.state.confirmation = ["gateway", "http-timeout"].includes(mode) ? mode : "hang";
        await f.confirm();
        if (mode === "stop") await f.page.locator("#stopChat").click();
        await f.page.locator(".order-lookup-button").waitFor();
        assert.equal(f.confirmationCount(), 1);
      } finally { await f.close(); }
    });
  }
  await test("a later current draft never replaces the captured confirmed ID or gets marked submitted by the old receipt", async () => {
    const f = await fixture({ loggedIn: true });
    try {
      await f.confirm(); await f.page.locator(".order-lookup-button").waitFor();
      f.state.current = "new-current-b"; await f.send("再看另一份报价");
      await f.page.locator(".order-lookup-button").click(); await f.page.locator(".is-receipt").waitFor();
      const lookup = f.state.requests.find((r) => r.method === "GET");
      assert.equal(lookup.path, "/order-drafts/submitted-a/order");
      assert.equal(lookup.headers["x-user-token"], "fixture-token");
      assert(await f.page.locator(".tool-card.is-quote").last().locator(".quote-actions .btn-primary").isEnabled());
      f.state.reply = "plain"; await f.send("继续聊");
      assert.equal(f.state.requests.at(-1).body.currentDraftId, "new-current-b");
      assert.equal(f.confirmationCount(), 1);
    } finally { await f.close(); }
  });
  await test("new conversation preserves pending recovery and a pending query resolves in its new location", async () => {
    const f = await fixture();
    try {
      await f.confirm(); await f.page.locator(".order-lookup-button").waitFor(); f.state.lookup = "pending";
      await f.page.locator(".order-lookup-button").click();
      await f.page.waitForFunction(() => document.querySelector(".order-recovery").getAttribute("aria-busy") === "true");
      await f.page.locator("#newConversation").click();
      assert.equal(await f.page.locator(".order-lookup-button").count(), 1);
      f.state.lookup = "found"; f.state.releases.splice(0).forEach((resolve) => resolve());
      await f.page.locator(".is-receipt").waitFor(); assert.equal(f.confirmationCount(), 1);
    } finally { await f.close(); }
  });
  await test("duplicate lookup clicks are suppressed; switching identity aborts and discards the late receipt", async () => {
    const f = await fixture();
    try {
      await f.confirm(); await f.page.locator(".order-lookup-button").waitFor(); f.state.lookup = "pending";
      await f.page.locator(".order-lookup-button").click();
      await f.page.waitForFunction(() => document.querySelector(".order-recovery").getAttribute("aria-busy") === "true");
      await f.page.locator(".order-lookup-button").evaluate((button) => { button.click(); button.click(); });
      assert.equal(f.state.requests.filter((r) => r.method === "GET").length, 1);
      await f.page.evaluate(() => {
        localStorage.setItem("ordering_user", JSON.stringify({ userId: 42, token: "different-user" }));
        window.dispatchEvent(new StorageEvent("storage", { key: "ordering_user" }));
      });
      f.state.lookup = "found"; f.state.releases.splice(0).forEach((resolve) => resolve());
      await f.page.waitForFunction(() => document.querySelector("#agentStatus").textContent === "点餐助手已就绪");
      assert.equal(await f.page.locator(".order-recovery,.is-receipt").count(), 0);
      assert.equal(f.confirmationCount(), 1);
    } finally { await f.close(); }
  });
} finally {
  await browser.close();
  await new Promise((resolve) => server.close(resolve));
}
