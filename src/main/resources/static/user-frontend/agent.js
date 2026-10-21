import { streamAgentChat } from "./sse-client.mjs";
import { escapeHtml as esc, formatMessage } from "./message-format.mjs";
import { canConfirmDraft, confirmedDraftRequest, candidatePayload } from "./agent-policy.mjs";

const ui = window.OrderingUI;
const $ = (selector) => document.querySelector(selector);
const timeline = $("#chatTimeline");
const input = $("#chatInput");
const panel = $(".chat-panel");
const dialog = $("#agentConfirmDialog");
const spicyNames = { none: "不辣", mild: "微辣", medium: "中辣", high: "很辣" };
const chat = { conversationId: null, busy: false, saving: false, epoch: 0, request: null,
  draftCards: [], proposalCards: [], confirming: null, identity: identity(), profileLoad: 0 };

function identity() { const user = ui.getUser(); return user?.token ? `user:${user.userId}` : "guest"; }
function money(value) { return value !== null && value !== undefined && Number.isFinite(Number(value)) ? `¥${Number(value).toFixed(2)}` : "—"; }
function textElement(tag, value, className) {
  const element = document.createElement(tag);
  element.textContent = value;
  if (className) element.className = className;
  return element;
}
function icon(name) {
  const element = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  element.setAttribute("class", "icon");
  element.setAttribute("aria-hidden", "true");
  const use = document.createElementNS("http://www.w3.org/2000/svg", "use");
  use.setAttribute("href", `#i-${name}`);
  element.append(use);
  return element;
}
function nearBottom() { return timeline.scrollHeight - timeline.scrollTop - timeline.clientHeight < 90; }
function scrollLatest(force = false) {
  if (force || nearBottom()) timeline.scrollTop = timeline.scrollHeight;
  $("#jumpToLatest").classList.toggle("hidden", nearBottom());
}
function notice(message, newChat = false) {
  const box = $("#chatNotice");
  box.replaceChildren();
  box.classList.toggle("hidden", !message);
  if (!message) return;
  box.append(document.createTextNode(message));
  if (newChat) {
    const button = textElement("button", "开启新对话");
    button.type = "button";
    button.addEventListener("click", () => resetConversation());
    box.append(button);
  }
}
function updateControls() {
  const busy = chat.busy || chat.saving;
  if (input.getClientRects().length) {
    input.style.height = "auto";
    input.style.height = `${Math.min(input.scrollHeight, 150)}px`;
  }
  $("#sendChat").disabled = busy || !input.value.trim();
  $("#newConversation").disabled = busy;
  $("#clearTaste").disabled = busy;
  $("#stopChat").classList.toggle("hidden", !chat.busy);
  panel.classList.toggle("is-streaming", chat.busy);
  $("#chatInputCount").textContent = `${input.value.length} / 2000`;
  $("#chatForm").classList.toggle("has-input", !!input.value.length);
  $("#chatInputCount").classList.toggle("is-near-limit", input.value.length >= 1800);
  chat.draftCards.forEach((entry) => {
    entry.button.disabled = busy || entry.superseded || entry.submitted || entry.epoch !== chat.epoch
      || !canConfirmDraft(entry.result);
    if (!entry.superseded && !entry.submitted && !canConfirmDraft(entry.result)
        && entry.result.status === "SUCCESS") entry.button.textContent = "报价已过期，请重新试算";
  });
  chat.proposalCards.forEach((entry) => {
    entry.button.disabled = busy || entry.finished || entry.superseded || entry.epoch !== chat.epoch;
    entry.dismiss.disabled = busy || entry.finished || entry.superseded || entry.epoch !== chat.epoch;
  });
}
function resetConversation(message = "") {
  chat.epoch++;
  if (chat.request) { chat.request.reason = "identity"; chat.request.controller.abort(); }
  chat.request = null;
  chat.conversationId = null;
  chat.busy = false;
  chat.saving = false;
  chat.draftCards = [];
  chat.proposalCards = [];
  chat.confirming = null;
  dialog.close();
  $("#clearTasteDialog").close();
  timeline.querySelectorAll(".chat-message").forEach((element) => element.remove());
  $("#chatWelcome").classList.remove("hidden");
  $("#agentStatus").textContent = "点餐助手已就绪";
  input.value = "";
  notice(message);
  updateControls();
  scrollLatest(true);
}
function addMessage(role, value = "") {
  $("#chatWelcome").classList.add("hidden");
  const article = document.createElement("article");
  article.className = `chat-message is-${role}`;
  const label = textElement("div", role === "user" ? "你" : "", "message-label");
  if (role !== "user") {
    const avatar = textElement("span", "", "message-monogram");
    avatar.append(icon("bowl"));
    const now = new Date();
    const time = textElement("time", new Intl.DateTimeFormat("zh-CN", { hour: "2-digit", minute: "2-digit", hourCycle: "h23" }).format(now), "message-time");
    time.dateTime = now.toISOString();
    label.append(avatar, document.createTextNode("点餐助手"), time);
  }
  const content = textElement("div", value, "message-content");
  const state = textElement("p", "", "message-state");
  article.append(label, content, state);
  timeline.append(article);
  return { article, content, state };
}
function addReplyActions(assistant, reply) {
  const actions = textElement("div", "", "message-tools");
  const copy = textElement("button", "", "copy-reply");
  copy.type = "button";
  copy.setAttribute("aria-label", "复制助手回复");
  copy.setAttribute("aria-live", "polite");
  const label = textElement("span", "复制回复");
  copy.append(icon("copy"), label);
  copy.addEventListener("click", async () => {
    copy.disabled = true;
    try {
      await navigator.clipboard.writeText(reply);
      if (!copy.isConnected) return;
      copy.replaceChildren(icon("check"), label);
      label.textContent = "已复制";
      copy.setAttribute("aria-label", "回复已复制");
      copy.classList.add("is-copied");
      setTimeout(() => {
        if (!copy.isConnected) return;
        copy.replaceChildren(icon("copy"), label);
        label.textContent = "复制回复";
        copy.setAttribute("aria-label", "复制助手回复");
        copy.classList.remove("is-copied");
        copy.disabled = false;
      }, 1800);
    } catch {
      if (copy.isConnected) {
        copy.disabled = false;
        ui.toast("复制未完成，请选中文字后复制");
      }
    }
  });
  actions.append(assistant.state, copy);
  assistant.article.append(actions);
}

async function sendMessage(message, confirmedDraftId = null) {
  message = message.trim();
  if (chat.busy || chat.saving || !message) return;
  if (message.length > 2000) { ui.toast("请把消息控制在2000字以内"); return; }
  const epoch = chat.epoch;
  const request = { controller: new AbortController(), reason: null };
  const user = ui.getUser();
  const shouldScroll = nearBottom();
  addMessage("user", message);
  const assistant = addMessage("assistant");
  assistant.article.classList.add("is-pending");
  chat.busy = true;
  chat.request = request;
  input.value = "";
  notice("");
  $("#agentStatus").textContent = "正在回复，稍等一会儿";
  updateControls();
  scrollLatest(shouldScroll);
  const timeout = setTimeout(() => { request.reason = "timeout"; request.controller.abort(); }, 75_000);
  let preview = "", streamStarted = false;
  try {
    await streamAgentChat({ baseUrl: ui.getApiBase(), token: user?.token, message,
      conversationId: chat.conversationId, confirmedDraftId, signal: request.controller.signal,
      onMeta(data) {
        if (epoch === chat.epoch) { chat.conversationId = data.conversationId; streamStarted = true; }
      },
      onDelta(fragment) {
        if (epoch !== chat.epoch) return;
        const follow = nearBottom();
        preview += fragment;
        // textContent while streaming avoids parsing incomplete Markdown and raw model HTML.
        assistant.content.textContent = preview;
        assistant.content.style.whiteSpace = "pre-wrap";
        scrollLatest(follow);
      },
      onDone(data) {
        if (epoch !== chat.epoch) return;
        const follow = nearBottom();
        assistant.content.style.whiteSpace = "";
        assistant.content.innerHTML = formatMessage(data.reply);
        assistant.state.textContent = "回复完成";
        renderToolResults(data, assistant.article);
        addReplyActions(assistant, data.reply);
        $("#agentStatus").textContent = "同一段对话，可以继续聊";
        scrollLatest(follow);
      },
    });
  } catch (error) {
    if (epoch !== chat.epoch) return;
    const stopped = request.reason === "user";
    const preflightRejected = !streamStarted && error.status >= 400;
    const description = preflightRejected ? `${error.message}。本次请求未开始处理。`
      : stopped ? "已停止接收。后台操作可能仍在执行，请勿重复提交订单。"
      : request.reason === "timeout" ? "等待回复超时。操作结果尚未确认，请勿重复提交订单。"
      : `${error.message || "连接中断"}。本次回复未完成，操作结果需要核实。`;
    if (!assistant.content.textContent) assistant.content.textContent = preflightRejected ? "这次请求没有开始处理。" : stopped ? "已停止接收这次回复。" : "这次没能收到完整回复。";
    assistant.state.textContent = description;
    assistant.state.classList.add("is-error");
    $("#agentStatus").textContent = preflightRejected ? "暂时无法开始对话" : stopped ? "接收已停止" : "本次回复未完成";
    if (error.status === 401 && ui.getUser()?.token === user?.token) ui.handleAuthExpired();
    else if (error.status === 403 || error.status === 404 || (error.status === 409 && /失效/.test(error.message))) {
      notice(error.message, true);
    } else notice(description);
  } finally {
    clearTimeout(timeout);
    if (epoch === chat.epoch) {
      assistant.article.classList.remove("is-pending");
      chat.busy = false;
      chat.request = null;
      updateControls();
      scrollLatest();
    }
  }
}

function quoteMarkup(result) {
  const quote = result.draft;
  const budgetState = quote.overBudget === true ? "已超出预算" : quote.overBudget === false ? "预算内" : "预算待核实";
  return `<div class="quote-section-title"><span>菜品清单</span><span>${quote.items.length} 项菜品</span></div>
    <ul class="quote-items">${quote.items.map((item, index) => `<li class="quote-line"><span class="quote-line-index">${String(index + 1).padStart(2, "0")}</span><div><b>${esc(item.dishName)}</b><small>${esc(money(item.unitPrice))} / 份 × ${esc(item.quantity)}</small></div><strong class="quote-line-total">${esc(money(item.subTotal))}</strong></li>`).join("")}</ul>
    <div class="quote-total"><span>本餐合计<small>以本次核算报价为准</small></span><strong>${esc(money(quote.totalAmount))}</strong></div>
    <p class="quote-budget"><span>${quote.budget == null ? "未设置预算" : `用餐预算 ${esc(money(quote.budget))}`}</span>${quote.budget == null ? "" : `<span class="budget-tag${quote.overBudget !== false ? " is-warning" : ""}">${budgetState}</span>`}</p>
    <details class="quote-metadata"><summary>报价编号与有效期<svg class="icon" aria-hidden="true"><use href="#i-chevron"/></svg></summary><p class="card-id">报价编号 ${esc(result.draftId)}<br />有效期至 ${esc(String(result.expireAt || "").replace("T", " "))}</p></details>`;
}
function card(title, kicker, badge, warning = false) {
  const element = document.createElement("section");
  const kind = kicker === "YOUR QUOTE" ? "quote" : kicker === "YOUR TASTE" ? "taste" : "receipt";
  const symbol = kind === "taste" ? "sliders" : kind === "receipt" && !warning ? "check" : "orders";
  element.className = `tool-card is-${kind}${warning ? " is-warning" : ""}`;
  element.innerHTML = `<div class="tool-card-header"><div class="tool-heading"><span class="tool-symbol"><svg class="icon" aria-hidden="true"><use href="#i-${symbol}"/></svg></span><div><p class="card-kicker">${esc(kicker)}</p><h3>${esc(title)}</h3></div></div><span class="card-badge${warning ? " warning" : ""}">${esc(badge)}</span></div>`;
  return element;
}
function renderToolResults(data, article) {
  if (data.draftToolResult) renderDraft(data.draftToolResult, article);
  if (data.orderToolResult) renderOrder(data.orderToolResult, article);
  if (data.proposalToolResult) renderProposal(data.proposalToolResult, article);
}
function renderDraft(result, article) {
  chat.draftCards.forEach((entry) => { entry.superseded = true; entry.button.textContent = "已有新试算，请核对最新报价"; });
  const valid = result.draftId && Array.isArray(result.draft?.items) && result.draft.items.length;
  const over = result.status === "BUDGET_EXCEEDED" || result.draft?.overBudget;
  const element = card(valid ? "这份搭配的报价" : "本次试算未完成", "YOUR QUOTE", over ? "超出预算" : valid ? "尚未下单" : "需要调整", !!over || !valid);
  if (valid) {
    const detail = document.createElement("div");
    detail.innerHTML = quoteMarkup(result);
    element.append(detail);
  } else element.append(textElement("p", result.message || "请调整菜品或份数后重新试算。"));
  if (valid) {
    const actions = document.createElement("div");
    actions.className = "card-actions";
    const button = textElement("button", over ? "超预算，请先调整方案" : "核对并确认下单", "btn-primary");
    button.type = "button";
    button.disabled = true;
    button.append(icon("arrow"));
    const entry = { result, button, epoch: chat.epoch, superseded: false, submitted: false };
    chat.draftCards.push(entry);
    button.addEventListener("click", () => {
      if (chat.busy || chat.saving || entry.superseded || entry.submitted || !canConfirmDraft(result)) return;
      chat.confirming = entry;
      $("#agentConfirmDetail").innerHTML = quoteMarkup(result);
      dialog.showModal();
      $("#cancelAgentOrder").focus();
    });
    actions.append(button);
    element.append(actions);
  }
  article.append(element);
}
function renderOrder(result, article) {
  const order = result.order;
  const success = result.status === "SUCCESS" && order?.orderNum != null;
  const cancelled = success && order.orderStatus === "cancelled";
  const element = card(cancelled ? "这份订单已取消" : success ? "订单已提交" : "这次未能确认下单", "ORDER RECEIPT", cancelled ? "已取消" : success ? "正式订单" : "请核实后再操作", !success || cancelled);
  if (success) {
    element.append(textElement("p", `订单号 ${order.orderNum} · ${({ pending: "待处理", paid: "已支付", cancelled: "已取消" })[order.orderStatus] || order.orderStatus}`));
    const list = document.createElement("ul");
    list.className = "quote-items";
    (order.items || []).forEach((item) => list.append(textElement("li", `${item.dishName} × ${item.quantity} · 单价 ${money(item.priceAtTime)}`)));
    element.append(list, textElement("div", `订单金额 ${money(order.orderTotalMoney)}`, "quote-total"));
    try { ui.rememberAgentOrder(order); }
    catch { element.append(textElement("p", "订单已返回，但本机回执未能保存，请记下订单号。")); }
    chat.draftCards.forEach((entry) => { entry.submitted = true; entry.button.textContent = "已返回正式订单，请查看回执"; });
  } else element.append(textElement("p", result.message || "请根据助手说明核实结果，不要重复提交。"));
  article.append(element);
}
function renderProposal(result, article) {
  chat.proposalCards.forEach((entry) => { entry.superseded = true; entry.button.textContent = "已有新候选，请核对最新口味"; });
  let payload;
  try { payload = candidatePayload(result); } catch { /* Business failure is shown without a save button. */ }
  const element = card(payload ? "这份口味，要记住吗？" : "暂未生成口味候选", "YOUR TASTE", payload ? "待你确认" : "尚未保存", !payload);
  if (payload) {
    element.insertAdjacentHTML("beforeend", `<dl class="proposal-fields"><div><dt>辣度</dt><dd>${esc(spicyNames[payload.spicyLevel] || "未设置")}</dd></div><div><dt>其他口味</dt><dd>${esc(payload.preferenceText || "未设置")}</dd></div></dl>`);
    element.append(textElement("p", "保存会替换原长期口味；本次人数和预算不会被记住。"));
    const actions = document.createElement("div");
    actions.className = "card-actions";
    const save = textElement("button", "确认保存口味", "btn-primary");
    save.type = "button";
    save.disabled = true;
    const dismiss = textElement("button", "暂不保存", "btn-ghost");
    dismiss.type = "button";
    dismiss.disabled = true;
    const entry = { button: save, dismiss, epoch: chat.epoch, superseded: false, finished: false };
    chat.proposalCards.push(entry);
    dismiss.addEventListener("click", () => { entry.finished = true; save.textContent = "这次不保存"; dismiss.disabled = true; updateControls(); });
    save.addEventListener("click", async () => {
      if (chat.busy || chat.saving || entry.finished || entry.superseded || entry.epoch !== chat.epoch) return;
      const token = ui.getUser()?.token;
      if (!token) { ui.openLogin(); return; }
      chat.saving = true;
      updateControls();
      try {
        await ui.api("/users/me/preference", { method: "PUT", headers: { "X-User-Token": token }, body: JSON.stringify(payload) });
        if (entry.epoch !== chat.epoch) return;
        entry.finished = true;
        save.textContent = "口味已保存";
        dismiss.disabled = true;
        ui.toast("长期口味已保存，下轮对话即可使用");
        await loadProfile();
      } catch { /* API helper displays the actual error; the candidate remains retryable. */ }
      finally { if (entry.epoch === chat.epoch) { chat.saving = false; updateControls(); } }
    });
    actions.append(save, dismiss);
    element.append(actions);
  } else {
    element.append(textElement("p", result.message || "请重新说明想要保存的长期口味。"));
    if (result.status === "LOGIN_REQUIRED") {
      const login = textElement("button", "登录后设置口味", "taste-login");
      login.type = "button";
      login.addEventListener("click", () => ui.openLogin());
      element.append(login);
    }
  }
  article.append(element);
}

async function loadProfile() {
  const requestNumber = ++chat.profileLoad;
  const user = ui.getUser();
  const box = $("#savedTaste");
  $("#clearTaste").classList.add("hidden");
  box.replaceChildren();
  if (!user?.token) {
    box.append(textElement("p", "记住喜欢的味道，下次更懂你。"));
    const login = textElement("button", "登录，开启口味记忆", "taste-login");
    login.type = "button";
    login.addEventListener("click", () => ui.openLogin());
    box.append(login);
    return;
  }
  box.textContent = "正在读取你的口味……";
  try {
    const profile = await ui.api("/users/me/preference", { headers: { "X-User-Token": user.token } });
    if (requestNumber !== chat.profileLoad) return;
    box.replaceChildren();
    if (!profile || (profile.spicyLevel == null && !profile.preferenceText)) {
      box.append(textElement("p", "还没有口味记录。和助手聊聊喜欢的辣度与味道吧。"));
    } else {
      box.append(textElement("span", spicyNames[profile.spicyLevel] || "辣度未设置", "taste-tag"));
      box.append(textElement("p", profile.preferenceText || "暂无其他口味说明", "taste-value"));
    }
    $("#clearTaste").classList.toggle("hidden", !profile);
  } catch { if (requestNumber === chat.profileLoad) box.textContent = "暂时无法读取口味，可以稍后刷新。"; }
}

$("#chatForm").addEventListener("submit", (event) => { event.preventDefault(); sendMessage(input.value); });
input.addEventListener("input", updateControls);
input.addEventListener("keydown", (event) => {
  if (event.key === "Enter" && !event.shiftKey && !event.isComposing) { event.preventDefault(); sendMessage(input.value); }
});
$("#stopChat").addEventListener("click", () => {
  if (chat.request) { chat.request.reason = "user"; chat.request.controller.abort(); }
});
$("#newConversation").addEventListener("click", () => { if (!chat.busy && !chat.saving) resetConversation(); });
$("#agentBrowseMenu").addEventListener("click", () => ui.switchPage("menu"));
// Reuse the same preference controls in a native drawer on smaller screens.
const diningDialog = $("#diningPanelDialog");
const diningSidebar = $("#diningSidebar");
const diningLayout = $(".agent-layout");
$("#openDiningPanel").addEventListener("click", () => {
  diningDialog.append(diningSidebar);
  diningDialog.showModal();
});
$("#closeDiningPanel").addEventListener("click", () => diningDialog.close());
diningDialog.addEventListener("close", () => diningLayout.append(diningSidebar));
diningDialog.addEventListener("click", (event) => {
  if (event.target === diningDialog) diningDialog.close();
});
matchMedia("(min-width: 1181px)").addEventListener("change", (event) => {
  if (event.matches && diningDialog.open) diningDialog.close();
});
$("#refreshTaste").addEventListener("click", loadProfile);
$("#jumpToLatest").addEventListener("click", () => scrollLatest(true));
timeline.addEventListener("scroll", () => $("#jumpToLatest").classList.toggle("hidden", nearBottom()));
document.querySelectorAll("[data-chat-prompt]").forEach((button) => button.addEventListener("click", () => {
  input.value = button.dataset.chatPrompt;
  updateControls();
  input.focus();
}));
$("#cancelAgentOrder").addEventListener("click", () => dialog.close());
$("#clearTaste").addEventListener("click", () => {
  if (chat.busy || chat.saving || !ui.getUser()?.token) return;
  $("#clearTasteDialog").showModal();
  $("#cancelClearTaste").focus();
});
$("#cancelClearTaste").addEventListener("click", () => $("#clearTasteDialog").close());
$("#confirmClearTaste").addEventListener("click", async () => {
  $("#clearTasteDialog").close();
  const token = ui.getUser()?.token, epoch = chat.epoch;
  if (!token || chat.busy || chat.saving) return;
  chat.saving = true;
  updateControls();
  try {
    await ui.api("/users/me/preference", { method: "DELETE", headers: { "X-User-Token": token } });
    if (epoch !== chat.epoch) return;
    chat.proposalCards.forEach((entry) => { entry.superseded = true; entry.button.textContent = "口味已清除，请重新生成候选"; });
    ui.toast("长期口味已清除");
    await loadProfile();
  } catch { /* Keep the saved profile visible until the API confirms deletion. */ }
  finally { if (epoch === chat.epoch) { chat.saving = false; updateControls(); } }
});
$("#submitAgentOrder").addEventListener("click", () => {
  const entry = chat.confirming;
  dialog.close();
  if (!entry || entry.epoch !== chat.epoch || chat.busy || chat.saving || entry.superseded || entry.submitted) return;
  try {
    const request = confirmedDraftRequest(entry.result);
    sendMessage(request.message, request.confirmedDraftId);
  } catch (error) { ui.toast(error.message); updateControls(); }
});
window.addEventListener("ordering:auth-changed", () => {
  const next = identity();
  if (next !== chat.identity) {
    chat.identity = next;
    resetConversation("身份已切换，已开启新对话。");
  }
  loadProfile();
});
window.addEventListener("ordering:page-changed", (event) => {
  if (diningDialog.open) diningDialog.close();
  if (event.detail === "agent") { loadProfile(); updateControls(); scrollLatest(); }
});
setInterval(() => { if (chat.draftCards.length) updateControls(); }, 15_000);
loadProfile();
updateControls();
