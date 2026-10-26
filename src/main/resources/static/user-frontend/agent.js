import { streamAgentChat } from "./sse-client.mjs";
import { escapeHtml as esc, formatMessage } from "./message-format.mjs";
import { canConfirmDraft, confirmedDraftRequest, candidatePayload } from "./agent-policy.mjs";
import { requestDraftRevision } from "./draft-revision.mjs";

const ui = window.OrderingUI;
const $ = (selector) => document.querySelector(selector);
const timeline = $("#chatTimeline");
const input = $("#chatInput");
const panel = $(".chat-panel");
const dialog = $("#agentConfirmDialog");
const jumpButton = $("#jumpToLatest");
const starterButtons = [...document.querySelectorAll("[data-chat-prompt]")];
const chatView = { followLatest: true, hasUnread: false };
let selectedStarter = null;
const spicyNames = { none: "不辣", mild: "微辣", medium: "中辣", high: "很辣" };
const chat = { conversationId: null, currentDraftId: null, busy: false, saving: false, epoch: 0, request: null,
  draftCards: [], proposalCards: [], confirming: null, revision: null, identity: identity(), profileLoad: 0 };

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
function nearBottom() { return timeline.scrollHeight - timeline.scrollTop - timeline.clientHeight < 16; }
function updateJumpButton() {
  const visible = !chatView.followLatest && timeline.scrollHeight > timeline.clientHeight;
  jumpButton.classList.toggle("hidden", !visible);
  jumpButton.classList.toggle("has-unread", chatView.hasUnread);
  const label = chatView.hasUnread ? "有新回复，点击查看" : "回到最新回复";
  const text = jumpButton.querySelector(".jump-label");
  if (text.textContent !== label) text.textContent = label;
  jumpButton.setAttribute("aria-label", label);
}
function scrollLatest(force = false, newReply = false) {
  if (force) chatView.followLatest = true;
  if (newReply && !chatView.followLatest) chatView.hasUnread = true;
  // Follow the reader's scroll choice, not the distance after a reply changes height.
  if (chatView.followLatest && timeline.getClientRects().length) {
    timeline.scrollTop = timeline.scrollHeight;
    chatView.hasUnread = false;
  }
  updateJumpButton();
}
function syncStarterSelection() {
  const selected = starterButtons.find((button) => button.dataset.chatPrompt === input.value.trim()) || null;
  starterButtons.forEach((button) => {
    const active = button === selected;
    button.classList.toggle("is-selected", active);
    button.setAttribute("aria-pressed", String(active));
    button.querySelector(".starter-arrow use").setAttribute("href", active ? "#i-check" : "#i-arrow");
  });
  if (selected !== selectedStarter) {
    selectedStarter = selected;
    $("#starterHint").textContent = selected
      ? `已填入「${selected.querySelector("b").textContent}」，可以修改后再发送。`
      : "点击卡片填入想法，发送后再开始聊。";
  }
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
  syncStarterSelection();
  chat.draftCards.forEach((entry) => updateDraftControls(entry, busy));
  chat.proposalCards.forEach((entry) => {
    entry.button.disabled = busy || entry.finished || entry.superseded || entry.epoch !== chat.epoch;
    entry.dismiss.disabled = busy || entry.finished || entry.superseded || entry.epoch !== chat.epoch;
  });
}
function resetConversation(message = "") {
  chat.epoch++;
  if (chat.request) { chat.request.reason = "identity"; chat.request.controller.abort(); }
  if (chat.revision) chat.revision.controller.abort();
  chat.revision = null;
  chat.request = null;
  chat.conversationId = null;
  chat.currentDraftId = null;
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
  addMessage("user", message);
  const assistant = addMessage("assistant");
  assistant.article.classList.add("is-pending");
  chat.busy = true;
  chat.request = request;
  input.value = "";
  notice("");
  $("#agentStatus").textContent = "正在回复，稍等一会儿";
  updateControls();
  scrollLatest();
  const timeout = setTimeout(() => { request.reason = "timeout"; request.controller.abort(); }, 75_000);
  let preview = "", streamStarted = false;
  try {
    await streamAgentChat({ baseUrl: ui.getApiBase(), token: user?.token, message,
      conversationId: chat.conversationId, currentDraftId: chat.currentDraftId,
      confirmedDraftId, signal: request.controller.signal,
      onMeta(data) {
        if (epoch === chat.epoch) { chat.conversationId = data.conversationId; streamStarted = true; }
      },
      onDelta(fragment) {
        if (epoch !== chat.epoch) return;
        preview += fragment;
        // textContent while streaming avoids parsing incomplete Markdown and raw model HTML.
        assistant.content.textContent = preview;
        assistant.content.style.whiteSpace = "pre-wrap";
        scrollLatest(false, true);
      },
      onDone(data) {
        if (epoch !== chat.epoch) return;
        assistant.content.style.whiteSpace = "";
        assistant.content.innerHTML = formatMessage(data.reply);
        assistant.state.textContent = "回复完成";
        renderToolResults(data, assistant.article);
        addReplyActions(assistant, data.reply);
        $("#agentStatus").textContent = "同一段对话，可以继续聊";
        scrollLatest(false, true);
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
    scrollLatest(false, true);
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
function draftIsCurrent(entry) {
  return entry.epoch === chat.epoch && !entry.superseded && !entry.submitted && !entry.unavailable && !entry.uncertain;
}
function draftCanBeEdited(entry) {
  return draftIsCurrent(entry) && ["SUCCESS", "BUDGET_EXCEEDED"].includes(entry.result.status)
    && Date.parse(entry.result.expireAt) > Date.now()
    && entry.result.draft.items.length <= 20 && entry.result.draft.items.every((item) =>
      Number.isSafeInteger(item.dishNumber) && item.dishNumber > 0
      && Number.isInteger(item.quantity) && item.quantity >= 1 && item.quantity <= 50);
}
function draftHasChanges(entry) {
  const original = entry.result.draft.items;
  return entry.items.length !== original.length || entry.items.some((item, index) =>
    item.dishNumber !== original[index].dishNumber || item.quantity !== original[index].quantity);
}
function updateDraftControls(entry, busy = chat.busy || chat.saving) {
  const editable = draftCanBeEdited(entry);
  const current = draftIsCurrent(entry);
  const expired = !(Date.parse(entry.result.expireAt) > Date.now());
  entry.button.disabled = busy || !current || entry.dirty || !canConfirmDraft(entry.result);
  let label = "核对并确认下单";
  if (entry.submitted) label = "已返回正式订单，请查看回执";
  else if (entry.superseded) label = "已被新报价替换";
  else if (entry.uncertain) label = "重新报价结果待核实";
  else if (entry.unavailable || expired) label = "报价已无法使用，请重新试算";
  else if (entry.dirty) label = "请先重新报价，再确认下单";
  else if (!canConfirmDraft(entry.result)) label = "超预算，请先调整方案";
  entry.button.replaceChildren(document.createTextNode(label), icon("arrow"));
  entry.revise.disabled = busy || !editable || !entry.dirty || !entry.items.length;
  entry.revise.textContent = chat.revision?.entry === entry ? "正在重新报价……" : "重新报价";
  entry.restore.disabled = busy || !editable || !entry.dirty;
  entry.editor.querySelectorAll("[data-quote-action]").forEach((button) => {
    const item = entry.items.find((value) => value.dishNumber === Number(button.dataset.dishNumber));
    button.disabled = busy || !editable || !item
      || (button.dataset.quoteAction === "minus" && item.quantity <= 1)
      || (button.dataset.quoteAction === "plus" && item.quantity >= 50);
  });
  entry.element.classList.toggle("is-superseded", entry.superseded || entry.submitted);
  entry.element.classList.toggle("has-quote-edits", entry.dirty);
  entry.element.setAttribute("aria-busy", String(chat.revision?.entry === entry));
  if (expired && current && chat.revision?.entry !== entry) entry.feedback.textContent = "报价已过期，请在对话中重新试算后再调整或下单。";
  const badge = entry.element.querySelector(".card-badge");
  badge.textContent = entry.submitted ? "已下单" : entry.superseded ? "已替换" : entry.uncertain ? "待核实"
    : entry.unavailable || expired ? "已失效" : entry.dirty ? "待重新报价" : entry.result.draft.overBudget ? "超出预算" : "尚未下单";
}
function renderQuoteEditor(entry, focus = null) {
  entry.dirty = draftHasChanges(entry);
  entry.editor.innerHTML = quoteMarkup(entry.result);
  const list = entry.editor.querySelector(".quote-items");
  list.replaceChildren();
  entry.editor.querySelector(".quote-section-title > span:last-child").textContent = `${entry.items.length} 项菜品`;
  entry.items.forEach((item, index) => {
    const original = entry.result.draft.items.find((value) => value.dishNumber === item.dishNumber);
    const row = document.createElement("li");
    row.className = "quote-line quote-edit-line";
    row.innerHTML = `<span class="quote-line-index">${String(index + 1).padStart(2, "0")}</span><div><b>${esc(original.dishName)}</b><small>${esc(money(original.unitPrice))} / 份${entry.dirty ? " · 上次报价单价" : ""}</small><div class="quote-quantity"><button type="button" data-quote-action="minus" data-dish-number="${esc(item.dishNumber)}" aria-label="减少${esc(original.dishName)}的份数">−</button><span class="quote-quantity-value">${item.quantity}</span><button type="button" data-quote-action="plus" data-dish-number="${esc(item.dishNumber)}" aria-label="增加${esc(original.dishName)}的份数">+</button><span class="quote-quantity-unit">份</span></div></div><div class="quote-item-end"><button type="button" data-quote-action="remove" data-dish-number="${esc(item.dishNumber)}" aria-label="删除${esc(original.dishName)}"><svg class="icon" aria-hidden="true"><use href="#i-close"/></svg></button><strong class="quote-line-total">${entry.dirty ? "待核价" : esc(money(original.subTotal))}</strong></div>`;
    list.append(row);
  });
  if (!entry.items.length) list.append(textElement("li", "请至少保留一道菜，或恢复原报价。", "quote-editor-empty"));
  if (entry.dirty) {
    entry.editor.querySelector(".quote-total strong").textContent = "待重新报价";
    entry.editor.querySelector(".quote-total small").textContent = "份数已修改，金额以重新报价结果为准";
    entry.editor.querySelector(".budget-tag")?.remove();
  }
  updateDraftControls(entry);
  if (focus) {
    const buttons = [...entry.editor.querySelectorAll("[data-quote-action]:not(:disabled)")];
    const target = buttons.find((button) => button.dataset.quoteAction === focus.action && button.dataset.dishNumber === focus.dishNumber)
      || buttons.find((button) => button.dataset.dishNumber === focus.dishNumber) || buttons[0]
      || (!entry.restore.disabled ? entry.restore : entry.revise);
    if (!target.disabled) target.focus({ preventScroll: true });
  }
}
async function reviseDraft(entry, article) {
  if (chat.busy || chat.saving || !draftCanBeEdited(entry) || !entry.dirty || !entry.items.length) return;
  const user = ui.getUser();
  const request = { controller: new AbortController(), entry };
  const epoch = chat.epoch;
  chat.revision = request;
  chat.saving = true;
  chat.confirming = null;
  dialog.close();
  entry.feedback.textContent = "正在按调整后的份数重新核价……";
  entry.feedback.classList.remove("is-error");
  updateControls();
  const timeout = setTimeout(() => request.controller.abort(), 35_000);
  try {
    const revised = await requestDraftRevision({ baseUrl: ui.getApiBase(), token: user?.token,
      draftId: entry.result.draftId, items: entry.items, signal: request.controller.signal });
    if (epoch !== chat.epoch || chat.revision !== request) return;
    const replacement = renderDraft(revised, article);
    entry.element.after(replacement);
    entry.items = entry.result.draft.items.map(({ dishNumber, quantity }) => ({ dishNumber, quantity }));
    renderQuoteEditor(entry);
    entry.feedback.textContent = "此报价已被替换，请核对下方新报价。";
    scrollLatest(true);
  } catch (error) {
    if (epoch !== chat.epoch || chat.revision !== request) return;
    entry.feedback.classList.add("is-error");
    if (error.uncertain || (!error.code && !error.status)) {
      entry.uncertain = true;
      entry.feedback.textContent = "重新报价结果尚未确认，请在对话中请求重新试算后再下单。请勿重复提交本次修改。";
    } else {
      const code = error.code || error.status;
      entry.unavailable = [401, 403, 404].includes(code) || (code === 409 && !/正在处理|稍后重试|下架/.test(error.message));
      entry.feedback.textContent = `重新报价未完成：${error.message || "请稍后再试"}${entry.unavailable ? "。请在对话中重新试算。" : "。可以调整后再次报价。"}`;
      if (code === 401 && user?.token && ui.getUser()?.token === user.token) ui.handleAuthExpired();
    }
  } finally {
    clearTimeout(timeout);
    if (epoch === chat.epoch && chat.revision === request) {
      chat.revision = null;
      chat.saving = false;
      updateControls();
    }
  }
}
function renderDraft(result, article) {
  chat.draftCards.forEach((entry) => { entry.superseded = true; entry.button.textContent = "已有新试算，请核对最新报价"; });
  const valid = result.draftId && Array.isArray(result.draft?.items) && result.draft.items.length;
  const over = result.status === "BUDGET_EXCEEDED" || result.draft?.overBudget;
  const element = card(valid ? "这份搭配的报价" : "本次试算未完成", "YOUR QUOTE", over ? "超出预算" : valid ? "尚未下单" : "需要调整", !!over || !valid);
  if (!valid) element.append(textElement("p", result.message || "请调整菜品或份数后重新试算。"));
  if (valid) {
    // 后续聊天只携带草稿引用，报价内容由后端按当前身份重新核验。
    chat.currentDraftId = result.draftId;
    const detail = document.createElement("div");
    detail.className = "quote-editor";
    const actions = document.createElement("div");
    actions.className = "card-actions quote-actions";
    const button = textElement("button", over ? "超预算，请先调整方案" : "核对并确认下单", "btn-primary");
    button.type = "button";
    button.disabled = true;
    button.append(icon("arrow"));
    const revise = textElement("button", "重新报价", "btn-ghost revise-quote");
    const restore = textElement("button", "恢复原报价", "restore-quote");
    revise.type = restore.type = "button";
    const feedback = textElement("p", "可调整份数或删除菜品，修改后请先重新报价。每道菜最多 50 份。", "quote-edit-feedback");
    feedback.setAttribute("role", "status");
    const entry = { result, button, revise, restore, feedback, editor: detail, element,
      items: result.draft.items.map(({ dishNumber, quantity }) => ({ dishNumber, quantity })),
      dirty: false, epoch: chat.epoch, superseded: false, submitted: false };
    chat.draftCards.push(entry);
    detail.addEventListener("click", (event) => {
      const control = event.target.closest("[data-quote-action]");
      if (!control || control.disabled || chat.busy || chat.saving || !draftCanBeEdited(entry)) return;
      const dishNumber = Number(control.dataset.dishNumber);
      const item = entry.items.find((value) => value.dishNumber === dishNumber);
      if (!item) return;
      if (control.dataset.quoteAction === "remove") entry.items = entry.items.filter((value) => value !== item);
      else item.quantity = Math.max(1, Math.min(50, item.quantity + (control.dataset.quoteAction === "plus" ? 1 : -1)));
      entry.feedback.classList.remove("is-error");
      entry.feedback.textContent = "份数已调整，重新报价后才能确认下单。";
      renderQuoteEditor(entry, { action: control.dataset.quoteAction, dishNumber: control.dataset.dishNumber });
    });
    restore.addEventListener("click", () => {
      if (chat.busy || chat.saving || !draftCanBeEdited(entry)) return;
      entry.items = result.draft.items.map(({ dishNumber, quantity }) => ({ dishNumber, quantity }));
      entry.feedback.classList.remove("is-error");
      entry.feedback.textContent = "已恢复本次报价的原始份数与金额。";
      renderQuoteEditor(entry);
    });
    revise.addEventListener("click", () => reviseDraft(entry, article));
    button.addEventListener("click", () => {
      if (chat.busy || chat.saving || !draftIsCurrent(entry) || entry.dirty || !canConfirmDraft(result)) return;
      chat.confirming = entry;
      $("#agentConfirmDetail").innerHTML = quoteMarkup(result);
      dialog.showModal();
      $("#cancelAgentOrder").focus();
    });
    const editActions = document.createElement("div");
    editActions.className = "quote-edit-actions";
    editActions.append(restore, revise);
    actions.append(button);
    element.append(detail, feedback, editActions, actions);
    renderQuoteEditor(entry);
  }
  article.append(element);
  return element;
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
    chat.currentDraftId = null;
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
jumpButton.replaceChildren(textElement("span", "回到最新回复", "jump-label"), icon("arrow"));
jumpButton.setAttribute("aria-controls", timeline.id);
jumpButton.setAttribute("aria-live", "polite");
jumpButton.setAttribute("aria-atomic", "true");
jumpButton.addEventListener("click", () => scrollLatest(true));
timeline.addEventListener("scroll", () => {
  if (!timeline.getClientRects().length) return;
  chatView.followLatest = nearBottom();
  if (chatView.followLatest) chatView.hasUnread = false;
  updateJumpButton();
}, { passive: true });
const starterGroups = [starterButtons.map((button) => ({
  title: button.querySelector("b").textContent,
  description: button.querySelector("small").textContent,
  prompt: button.dataset.chatPrompt,
  icon: button.querySelector(".starter-icon use").getAttribute("href"),
})), [
  { title: "一个人，也好好吃", description: "一人食 · 简单又满足", icon: "#i-bowl", prompt: "一个人吃饭，预算50元，想简单吃得舒服。请先推荐，不要试算或下单。" },
  { title: "朋友来了，吃点好的", description: "三五好友 · 分享好味道", icon: "#i-user", prompt: "四个朋友一起吃饭，预算200元，想搭配适合分享的菜。请先询问我们的口味，再推荐，不要试算或下单。" },
  { title: "来一碗暖暖的汤", description: "热汤暖胃 · 清淡些就好", icon: "#i-bowl", prompt: "今天想吃清淡一点，配一碗热汤。请查询在售菜单推荐合适的菜，不要试算或下单。" },
]];
let starterGroup = 0;
$("#refreshStarters").addEventListener("click", () => {
  starterGroup = (starterGroup + 1) % starterGroups.length;
  starterButtons.forEach((button, index) => {
    const idea = starterGroups[starterGroup][index];
    button.querySelector("b").textContent = idea.title;
    button.querySelector("small").textContent = idea.description;
    button.querySelector(".starter-icon use").setAttribute("href", idea.icon);
    button.dataset.chatPrompt = idea.prompt;
  });
  syncStarterSelection();
  $("#starterHint").textContent = `已换好第 ${starterGroup + 1} 组灵感，选一个聊聊吧。`;
});
starterButtons.forEach((button) => button.addEventListener("click", () => {
  input.value = button.dataset.chatPrompt;
  updateControls();
  input.focus();
}));
document.querySelectorAll("[data-go-page]").forEach((button) => button.addEventListener("click", () => ui.switchPage(button.dataset.goPage)));
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
  if (!entry || !draftIsCurrent(entry) || entry.dirty || chat.busy || chat.saving) return;
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
