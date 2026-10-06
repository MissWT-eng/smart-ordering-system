/* =========================================================
 * 智能点餐系统 · 前端（Agent-first）
 * 纯原生 JS，后端地址 http://localhost:8080
 * 统一响应结构：{ code: 0, msg: "success", data: {...} }
 * 首页双形态：提问态 ⇄ 结果态（对话式切换）
 * 收起结果不丢数据，可一键返回，不重复请求
 * ========================================================= */

// 前端由 Spring Boot 托管（/static/user-frontend），同源直接用相对路径；
// 万一用 file:// 直接打开，再退回 localhost:8080，避免双击 html 就废了
const API_BASE =
  location.protocol === "file:" ? "http://localhost:8080" : "";

/* 分类占位图（后端没有图片字段，用 emoji + 素色纸底） */
const CATEGORY_MAP = {
  meat:      { emoji: "🥩", label: "荤菜" },
  vegetable: { emoji: "🥬", label: "素菜" },
  drink:     { emoji: "🥤", label: "饮品" },
  soup:      { emoji: "🍲", label: "汤羹" },
};
function categoryOf(dish) {
  return CATEGORY_MAP[dish.category] || { emoji: "🍽️", label: "其他" };
}

/* ---------- 全局状态 ---------- */
const state = {
  user: JSON.parse(localStorage.getItem("ordering_user") || "null"), // 登录用户
  cart: new Map(),   // dishNumber -> { dish, qty }
  serverOrders: [],  // 最近一次拉取的账号订单，供「再来一单 / 评分」取用
  page: "home",
  // all：一次性拉回的在售菜品缓存；分页和分类都在前端做
  menu: { pageNum: 1, pageSize: 12, totalPages: 1, category: "", all: null },
};

/* ---------- 工具函数 ---------- */
const $ = (sel) => document.querySelector(sel);

/** 轻提示 */
let toastTimer = null;
function toast(msg) {
  const el = $("#toast");
  el.textContent = msg;
  el.classList.remove("hidden");
  // 重新触发入场动画
  el.style.animation = "none";
  void el.offsetWidth;
  el.style.animation = "";
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.add("hidden"), 2200);
}

/** 统一请求封装：自动处理 code/msg，非 0 弹 msg 并抛错；401 自动清登录态 */
async function api(path, options = {}) {
  let res;
  try {
    res = await fetch(API_BASE + path, {
      ...options,
      headers: { "Content-Type": "application/json", ...(options.headers || {}) },
    });
  } catch (e) {
    // 后端没起 / 断网：不能静默失败，必须让用户知道
    toast("连不上后端，请确认 8080 已启动");
    throw e;
  }
  if (!res.ok && res.status === 401) {
    handleAuthExpired();
    toast("登录已失效，请重新登录");
    throw new Error("unauthorized");
  }
  let body;
  try {
    body = await res.json();
  } catch (e) {
    toast("后端返回异常（非 JSON）");
    throw e;
  }
  if (body.code !== 0) {
    if (body.code === 401) handleAuthExpired(); // token 失效：清登录态并引导重新登录
    toast(body.msg || "请求失败");
    throw new Error(body.msg || "request failed");
  }
  return body.data;
}

/** 已登录用户的鉴权头（X-User-Token） */
function authHeaders() {
  return state.user && state.user.token ? { "X-User-Token": state.user.token } : {};
}

/** 401：token 过期/无效，清空本地登录态 */
function handleAuthExpired() {
  if (!state.user) return;
  state.user = null;
  localStorage.removeItem("ordering_user");
  renderLoginBtn();
  openModal("loginModal");
  refreshCurrentPage(); // 订单页要退回"去登录"空态，不能留着旧账号的单
}

const fmtMoney = (n) => "¥" + Number(n).toFixed(2);

/* ---------- 弹窗开关：统一入口 ----------
 * 打开时锁住背景滚动 —— 否则手机上弹窗里滚到底会带着整页一起动，
 * 关掉弹窗时人已经不在原来的位置了 */
function openModal(id) {
  $("#" + id).classList.remove("hidden");
  document.body.classList.add("no-scroll");
}
function closeModal(id) {
  $("#" + id).classList.add("hidden");
  // 还有别的弹窗开着就不解锁（比如登录弹窗压在订单弹窗上）
  if (!document.querySelector(".modal-mask:not(.hidden)")) {
    document.body.classList.remove("no-scroll");
  }
}

/** HTML 转义：后端文案里出现 < > & 等字符时不至于串排版 */
const esc = (s) =>
  String(s ?? "").replace(/[&<>"']/g, (c) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
  })[c]);

/* =========================================================
 * 页面路由（单页切换）
 * ========================================================= */
function switchPage(page) {
  state.page = page;
  document.querySelectorAll(".page").forEach((p) => p.classList.remove("active"));
  $("#page-" + page).classList.add("active");
  document.querySelectorAll(".nav-btn[data-page]").forEach((b) =>
    b.classList.toggle("active", b.dataset.page === page)
  );
  if (page === "menu") loadMenu({ refresh: true }); // 每次进菜单页拉一次最新的菜
  if (page === "orders") { loadOrders(); startOrderPolling(); } else { stopOrderPolling(); }
  if (page === "home" || page === "menu") refreshCartUI(); // 跨页回来时同步勾选
}

document.querySelectorAll(".nav-btn[data-page]").forEach((btn) => {
  btn.addEventListener("click", () => switchPage(btn.dataset.page));
});

/* =========================================================
 * 登录 / 注册（手机号识别）
 * ========================================================= */
$("#navLoginBtn").addEventListener("click", () => {
  if (state.user) {
    openUserModal();
    return;
  }
  openModal("loginModal");
  setTimeout(() => $("#loginPhone").focus(), 100);
});

$("#loginSubmit").addEventListener("click", submitLogin);
// 输入框回车直接登录
$("#loginPhone").addEventListener("keydown", (e) => { if (e.key === "Enter") submitLogin(); });
$("#loginNick").addEventListener("keydown", (e) => { if (e.key === "Enter") submitLogin(); });

async function submitLogin() {
  const phone = $("#loginPhone").value.trim();
  const nick = $("#loginNick").value.trim();
  if (!/^1\d{10}$/.test(phone)) {
    toast("请输入正确的 11 位手机号");
    return;
  }
  try {
    const data = await api("/users/identify", {
      method: "POST",
      body: JSON.stringify({ phoneNumber: phone, ...(nick ? { nickName: nick } : {}) }),
    });
    // data.token 是后续受保护接口的凭证（X-User-Token），必须存下来
    state.user = data;
    localStorage.setItem("ordering_user", JSON.stringify(data));
    closeModal("loginModal");
    renderLoginBtn();
    if (!data.token) toast("未返回 token，请重新登录");
    toast(`欢迎，${data.nickName || data.phoneNumber}`);
    refreshCurrentPage(); // 在订单页登录的话，立刻换成这个账号的订单
  } catch (e) { /* msg 已提示 */ }
}

function renderLoginBtn() {
  $("#navLoginBtn").textContent = state.user
    ? (state.user.nickName || state.user.phoneNumber)
    : "登录";
}

/** 登录态变化后刷新当前页：否则订单页会一直停在上一个账号的列表上 */
function refreshCurrentPage() {
  if (state.page === "orders") loadOrders();
}

/** 需要登录的操作先检查 */
/* =========================================================
 * 用户信息弹窗（打开时用 token 拉一次最新余额）
 * ========================================================= */
async function openUserModal() {
  const u = state.user;
  // 先用本地缓存渲染，再由接口校正余额（避免等请求时空白）
  renderUserModal(u);
  openModal("userModal");
  try {
    const fresh = await api(`/users/${u.userId}`, { headers: authHeaders() });
    // 接口返回不含 token（GET /users/{id} 不签发），保留本地 token
    state.user = { ...state.user, ...fresh, token: state.user.token };
    localStorage.setItem("ordering_user", JSON.stringify(state.user));
    renderUserModal(state.user);
  } catch (e) { /* 失败就用缓存数据，msg 已提示 */ }
}

function renderUserModal(u) {
  const name = u.nickName || "食客";
  // 印章式头像：取昵称首字
  $("#userAvatar").textContent = name.charAt(0);
  $("#userName").textContent = name;
  $("#userPhone").textContent = u.phoneNumber || "—";
  $("#userId").textContent = "#" + String(u.userId).padStart(4, "0");
  $("#userStatus").textContent = u.userStatus === "active" ? "正常" : (u.userStatus || "—");
  $("#userBalance").textContent = fmtMoney(u.balance ?? 0);
}

$("#logoutBtn").addEventListener("click", () => {
  state.user = null;
  localStorage.removeItem("ordering_user");
  closeModal("userModal");
  renderLoginBtn();
  refreshCurrentPage();
  toast("已退出登录");
});

/* =========================================================
 * 快捷灵感标签：可多选组合（点一下加、再点一下去掉）
 * 说明：词条只放口味/场景类，预算与人数交给上面的结构化输入框，
 *       避免"点了预算50、又填了预算80"这类说不清的冲突。
 * ========================================================= */
function preferenceTokens() {
  // 顿号、中英文逗号、分号、换行都算分隔符：
  // textarea 里回车换行是最自然的写法，不认换行的话整段会变成一个 token，
  // 快捷词条也就永远高亮不上
  return $("#preferenceInput").value
    .split(/[、,，;；\n\r]+/)
    .map((s) => s.trim())
    .filter(Boolean);
}

/** 输入框内容变化时，回写词条的高亮状态（手动输入同样的词也会亮） */
function refreshTagActive() {
  const tokens = new Set(preferenceTokens());
  document.querySelectorAll(".quick-tag").forEach((t) => {
    t.classList.toggle("active", tokens.has(t.textContent.trim()));
  });
}

$("#quickTags").addEventListener("click", (e) => {
  const tag = e.target.closest(".quick-tag");
  if (!tag) return;
  const text = tag.textContent.trim();
  const tokens = preferenceTokens();
  const idx = tokens.indexOf(text);
  if (idx >= 0) tokens.splice(idx, 1); // 已选中：去掉
  else tokens.push(text);              // 未选中：追加
  $("#preferenceInput").value = tokens.join("、");
  refreshTagActive();
  // 只给鼠标环境自动聚焦：手机上 focus 会立刻弹出键盘，挡住半屏
  if (window.matchMedia("(hover: hover)").matches) $("#preferenceInput").focus();
});

$("#preferenceInput").addEventListener("input", refreshTagActive);

/* =========================================================
 * 菜品卡片渲染（推荐 & 菜单共用）
 * @param dish       菜品对象
 * @param reason     推荐理由（仅推荐接口有）
 * @param primary    是否主推（仅推荐接口有）
 * ========================================================= */
function renderDishCard(dish, reason, primary) {
  const cat = categoryOf(dish);
  const item = state.cart.get(dish.dishNumber) || { qty: 1, checked: false };

  const card = document.createElement("div");
  card.className = "dish-card" + (primary ? " primary" : "");
  card.dataset.dishNumber = dish.dishNumber; // 供跨页同步勾选状态用
  card.innerHTML = `
    ${primary ? '<div class="primary-badge">主推</div>' : ""}
    <div class="dish-emoji ${dish.category || "other"}"><span aria-hidden="true">${cat.emoji}</span></div>
    <div class="dish-body">
      <div class="dish-head">
        <span class="dish-name">${esc(dish.dishName)}</span>
        <span class="dish-leader"></span>
        <span class="dish-price">${Number(dish.price).toFixed(2)}</span>
      </div>
      ${reason ? `<div class="dish-reason" tabindex="0" role="button" aria-expanded="false" aria-label="展开简介">${esc(reason)}</div>` : ""}
      <div class="dish-actions">
        <div class="stepper">
          <button class="step-minus" aria-label="减少数量" ${item.qty <= 1 ? "disabled" : ""}>−</button>
          <span class="qty">${item.qty}</span>
          <button class="step-plus" aria-label="增加数量">+</button>
        </div>
        <label class="check-label">
          <input type="checkbox" class="dish-check" ${item.checked ? "checked" : ""} /> 加入订单
        </label>
      </div>
    </div>
  `;

  // 数量加减
  const minus = card.querySelector(".step-minus");
  const plus = card.querySelector(".step-plus");
  const qtyEl = card.querySelector(".qty");
  const check = card.querySelector(".dish-check");

  function syncCart() {
    const qty = parseInt(qtyEl.textContent, 10);
    if (check.checked) {
      state.cart.set(dish.dishNumber, { dish, qty });
    } else {
      state.cart.delete(dish.dishNumber);
    }
    minus.disabled = qty <= 1;
    renderCartBar();
  }

  minus.addEventListener("click", () => {
    const q = parseInt(qtyEl.textContent, 10);
    if (q > 1) { qtyEl.textContent = q - 1; syncCart(); }
  });
  plus.addEventListener("click", () => {
    qtyEl.textContent = parseInt(qtyEl.textContent, 10) + 1;
    // 加数量时自动勾选，符合直觉
    if (!check.checked) check.checked = true;
    syncCart();
  });
  check.addEventListener("change", syncCart);

  // 简介点一下展开/收起（默认 3 行截断）；键盘 Enter/空格同样可触发
  const reasonEl = card.querySelector(".dish-reason");
  if (reasonEl) {
    const toggle = () => {
      const expanded = card.classList.toggle("expanded");
      reasonEl.setAttribute("aria-expanded", String(expanded));
      reasonEl.setAttribute("aria-label", expanded ? "收起简介" : "展开简介");
    };
    reasonEl.addEventListener("click", toggle);
    reasonEl.addEventListener("keydown", (e) => {
      if (e.key === "Enter" || e.key === " ") { e.preventDefault(); toggle(); }
    });
  }

  return card;
}

/* =========================================================
 * 购物车 UI 同步
 * 卡片是各自渲染的 DOM，勾选状态可能过期（比如在菜单页勾了菜，
 * 回首页推荐结果里同一道菜还是空的）。这里统一按 state.cart 回写。
 * ========================================================= */
function refreshCartUI() {
  document.querySelectorAll(".dish-card").forEach((card) => {
    const dishNumber = Number(card.dataset.dishNumber);
    if (!dishNumber) return;
    const item = state.cart.get(dishNumber);
    const qty = item ? item.qty : 1;
    card.querySelector(".qty").textContent = qty;
    card.querySelector(".dish-check").checked = !!item;
    card.querySelector(".step-minus").disabled = qty <= 1;
  });
  renderCartBar();
}

/* =========================================================
 * 核心：AI 推荐（成功后首页切换为「结果态」）
 * ========================================================= */
$("#recommendBtn").addEventListener("click", async () => {
  const preference = $("#preferenceInput").value.trim();
  if (!preference) {
    toast("先说说你想吃什么吧");
    return;
  }
  // 支持游客推荐：未登录时不带 userId 与 token；登录时带 userId + X-User-Token
  const payload = { preference };
  if (state.user) payload.userId = state.user.userId;
  const peopleNum = parseInt($("#peopleNumInput").value, 10);
  const budget = parseFloat($("#budgetInput").value);
  // 必须 >0：留空是 NaN 本来就跳过，但用户手抖填 0 会被当成"预算 0 元"，
  // 后端按 0 去砍菜，结果必然是空推荐
  if (Number.isFinite(peopleNum) && peopleNum > 0) payload.peopleNum = peopleNum;
  if (Number.isFinite(budget) && budget > 0) payload.budget = budget;

  const btn = $("#recommendBtn");
  btn.disabled = true;
  btn.textContent = "推荐中…";
  try {
    const data = await api("/recommendations", {
      method: "POST",
      headers: authHeaders(),
      body: JSON.stringify(payload),
    });

    $("#homeEmpty").classList.add("hidden");
    $("#backToResult").classList.add("hidden");
    $("#recommendResult").classList.remove("hidden");
    const summary = (data.summary || "").trim();
    $("#recommendSummary").textContent = summary;
    // 后端没给摘要就整段收掉：否则会孤零零挂一对「」引号在那儿
    $("#recommendSummary").classList.toggle("hidden", !summary);

    const box = $("#recommendCards");
    box.innerHTML = "";
    (data.dishes || []).forEach((it) => {
      box.appendChild(renderDishCard(it.dish, it.reason, it.primary));
    });
    // 推荐结果为空时给个空态，别留一片空白
    if (!data.dishes || data.dishes.length === 0) {
      box.innerHTML = '<div class="empty-tip" style="width:100%">这次没匹配到合适的菜，换个说法再试试</div>';
    }

    box.scrollLeft = 0;
    updateRailHint();
    refreshCartUI();

    // 对话式切换：首页进入结果态（hero 装饰退场，指令台收成顶部输入条）
    $("#page-home").classList.add("result-mode");
    window.scrollTo({ top: 0, behavior: "smooth" });
  } catch (e) { /* msg 已提示 */ } finally {
    btn.disabled = false;
    btn.textContent = "开始推荐";
  }
});

/* 收起结果：回到提问态，但结果保留在 DOM 里，可随时返回 */
$("#exitResult").addEventListener("click", () => {
  $("#page-home").classList.remove("result-mode");
  $("#recommendResult").classList.add("hidden");
  // 有结果可回：显示返回入口；没有结果才显示空提示
  if ($("#recommendCards").children.length > 0) {
    $("#backToResult").classList.remove("hidden");
  } else {
    $("#homeEmpty").classList.remove("hidden");
  }
  window.scrollTo({ top: 0, behavior: "smooth" });
});

/* 一键返回推荐结果：不重新请求，直接切回结果态 */
$("#backToResult").addEventListener("click", () => {
  $("#backToResult").classList.add("hidden");
  $("#recommendResult").classList.remove("hidden");
  $("#page-home").classList.add("result-mode");
  refreshCartUI(); // 可能在菜单页改过勾选，这里同步回来
  window.scrollTo({ top: 0, behavior: "smooth" });
});

/* =========================================================
 * 推荐卡轨：鼠标拖拽滑动 + 滚轮转横向 + 溢出才提示
 * 说明：滚动条被设计隐藏了，桌面上光靠滚轮滚不动，
 *       所以补三种滑动方式：拖拽、滚轮、触屏（原生）
 * ========================================================= */
const rail = $("#recommendCards");
const railWrap = document.querySelector(".rail-wrap");

/** 根据是否溢出/是否到底，更新提示文案与右边缘渐隐 */
function updateRailHint() {
  const max = rail.scrollWidth - rail.clientWidth;
  const hint = document.querySelector(".rail-hint");
  const overflowing = max > 8;
  const atEnd = rail.scrollLeft >= max - 8;
  hint.classList.toggle("hidden", !overflowing);
  hint.textContent = atEnd ? "已经到底了 ←" : "左右滑动查看 →";
  railWrap.classList.toggle("more-right", overflowing && !atEnd);
}
rail.addEventListener("scroll", updateRailHint);
window.addEventListener("resize", updateRailHint);
$("#exitResult").addEventListener("click", () => setTimeout(updateRailHint, 50));
$("#backToResult").addEventListener("click", () => setTimeout(updateRailHint, 50));

// 鼠标按住左右拖动（触屏交给浏览器原生滚动，不干预）
let dragging = false, dragStartX = 0, dragStartScroll = 0, dragMoved = 0;
rail.addEventListener("pointerdown", (e) => {
  if (e.pointerType !== "mouse") return;
  if (e.target.closest("button, input, label")) return; // 控件上不触发拖拽
  dragging = true;
  dragMoved = 0;
  dragStartX = e.clientX;
  dragStartScroll = rail.scrollLeft;
  rail.classList.add("dragging");
});
rail.addEventListener("pointermove", (e) => {
  if (!dragging) return;
  const dx = e.clientX - dragStartX;
  dragMoved = Math.max(dragMoved, Math.abs(dx));
  if (dragMoved > 5) rail.scrollLeft = dragStartScroll - dx;
});
["pointerup", "pointercancel", "pointerleave"].forEach((type) => {
  rail.addEventListener(type, () => {
    if (!dragging) return;
    dragging = false;
    rail.classList.remove("dragging");
  });
});
// 拖拽结束时抑制误点击（捕获阶段拦截）
rail.addEventListener("click", (e) => {
  if (dragMoved > 5) { e.stopPropagation(); e.preventDefault(); dragMoved = 0; }
}, true);

// 竖向滚轮转横向滚动；滚到两端后把滚动交还给页面
rail.addEventListener("wheel", (e) => {
  const max = rail.scrollWidth - rail.clientWidth;
  if (max <= 8) return;
  const vertical = Math.abs(e.deltaY) > Math.abs(e.deltaX);
  if (!vertical) return;
  const canScroll = (e.deltaY < 0 && rail.scrollLeft > 0) || (e.deltaY > 0 && rail.scrollLeft < max - 1);
  if (!canScroll) return; // 到头了，让页面继续竖向滚
  e.preventDefault();
  rail.scrollLeft += e.deltaY;
}, { passive: false });

/* =========================================================
 * 浏览菜单（兜底入口）：分页 + 分类筛选
 * ========================================================= */
const MENU_FETCH_SIZE = 200;

/**
 * 一次拉全量 → 前端分类 → 前端分页
 * 为什么不直接用后端分页：/dishes 没有分类参数，只能前端过滤。
 * 若是"后端分页 + 只过滤当前页"，第 1 页没有汤羹就会显示「这个分类暂时没有菜」，
 * 而第 2 页其实有 —— 用户会以为这家店不做汤。
 */
async function loadMenu(opts = {}) {
  if (opts.refresh) state.menu.all = null; // 进页面时拉最新；切分类/翻页走缓存
  $("#menuLoading").classList.remove("hidden");
  try {
    if (!state.menu.all) {
      const data = await api(`/dishes?pageNum=1&pageSize=${MENU_FETCH_SIZE}&status=available`);
      state.menu.all = data.list || [];
    }

    const { pageSize, category, all } = state.menu;
    const filtered = category ? all.filter((d) => d.category === category) : all;

    const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
    if (state.menu.pageNum > totalPages) state.menu.pageNum = totalPages;
    const pageNum = state.menu.pageNum;
    const list = filtered.slice((pageNum - 1) * pageSize, pageNum * pageSize);

    $("#menuCount").textContent = `共 ${filtered.length} 道菜`;

    const box = $("#menuCards");
    box.innerHTML = "";
    if (list.length === 0) {
      box.innerHTML = '<div class="empty-tip" style="grid-column:1/-1">这个分类暂时没有菜</div>';
    }
    // 菜单页没有推荐理由，改用菜品简介，点开可看全文
    list.forEach((dish) => box.appendChild(renderDishCard(dish, dish.dishIntroduction, false)));

    refreshCartUI(); // 按购物车回写勾选与数量（跨页回来时也不会丢）

    $("#menuPageInfo").textContent = `${pageNum} / ${totalPages}`;
    $("#menuPrev").disabled = pageNum <= 1;
    $("#menuNext").disabled = pageNum >= totalPages;
    // 只有一页时分页器是纯噪音（两个灰按钮 + "1 / 1"）
    $("#menuPager").classList.toggle("hidden", totalPages <= 1);
  } catch (e) { /* msg 已提示 */ } finally {
    $("#menuLoading").classList.add("hidden");
  }
}

$("#categoryFilter").addEventListener("click", (e) => {
  const chip = e.target.closest(".chip");
  if (!chip) return;
  document.querySelectorAll("#categoryFilter .chip").forEach((c) => c.classList.remove("active"));
  chip.classList.add("active");
  state.menu.category = chip.dataset.category;
  state.menu.pageNum = 1;
  loadMenu();
});
$("#menuPrev").addEventListener("click", () => { state.menu.pageNum--; loadMenu(); });
$("#menuNext").addEventListener("click", () => { state.menu.pageNum++; loadMenu(); });

/* =========================================================
 * 底部下单栏 + 下单
 * ========================================================= */
function renderCartBar() {
  let count = 0, total = 0;
  state.cart.forEach(({ dish, qty }) => {
    count += qty;
    total += dish.price * qty;
  });
  $("#cartCount").textContent = count;
  $("#cartTotal").textContent = fmtMoney(total);
  const empty = state.cart.size === 0;
  $("#cartBar").classList.toggle("hidden", empty);
  document.body.classList.toggle("has-cart", !empty); // 底部留白跟着购物车栏走
}

$("#checkoutBtn").addEventListener("click", async () => {
  if (state.cart.size === 0) {
    toast("先勾选想吃的菜");
    return;
  }

  const orderItems = [];
  state.cart.forEach(({ dish, qty }) => {
    orderItems.push({ dishNumber: dish.dishNumber, quantity: qty });
  });

  const btn = $("#checkoutBtn");
  btn.disabled = true;
  btn.textContent = "下单中…";
  // POST /orders 公开：游客不带 userId（后端支持游客单），登录用户带上 userId
  const orderBody = { orderItems };
  if (state.user) orderBody.userId = state.user.userId;

  try {
    const data = await api("/orders", {
      method: "POST",
      body: JSON.stringify(orderBody),
    });
    state.cart.clear();
    // 只清底部栏不清卡片的话，回到推荐结果会看到"勾着菜却没有下单栏"的鬼状态
    renderCartBar();
    refreshCartUI();
    showOrderSuccess(data);
  } catch (e) { /* msg 已提示 */ } finally {
    btn.disabled = false;
    btn.textContent = "去下单";
  }
});

/* ---------- 游客订单：存本机，补上"付完没凭条"的缺口 ---------- */
const GUEST_ORDER_KEY = "ordering_guest_orders";

function loadGuestOrders() {
  try {
    return JSON.parse(localStorage.getItem(GUEST_ORDER_KEY) || "[]");
  } catch (e) {
    return [];
  }
}

function saveGuestOrder(order) {
  const list = loadGuestOrders();
  list.unshift({
    orderNum: order.orderNum,
    orderTime: order.orderTime,
    orderStatus: order.orderStatus || "pending",
    orderTotalMoney: order.orderTotalMoney,
    items: order.items || [],
  });
  localStorage.setItem(GUEST_ORDER_KEY, JSON.stringify(list.slice(0, 20))); // 最多留 20 单
}

/** 订单卡片底部操作区：再来一单 / 评分（评分需要登录，由 openRateModal 再拦一次） */
function orderActionsHtml(idx) {
  return `
    <div class="order-actions">
      <button class="btn-ghost btn-xs" data-action="reorder" data-index="${idx}">再来一单</button>
      <button class="btn-ghost btn-xs" data-action="rate" data-index="${idx}">评分</button>
    </div>
  `;
}

/** 渲染本机游客订单（登录与否都显示，和账号订单区分开） */
function renderGuestOrders() {
  const list = loadGuestOrders();
  const section = $("#guestSection");
  const box = $("#guestOrderList");
  section.classList.toggle("hidden", list.length === 0);
  if (list.length === 0) return;

  box.innerHTML = list.map((order, idx) => `
    <div class="order-card">
      <div class="order-head">
        <span>游客单 #${order.orderNum} · ${(order.orderTime || "").replace("T", " ")}</span>
        <span class="order-status status-${esc(order.orderStatus || "")}">${ORDER_STATUS_TEXT[order.orderStatus] || order.orderStatus}</span>
      </div>
      <div class="order-items">
        ${order.items.map((i) => `<div>${esc(i.dishName)} ×${i.quantity}　${fmtMoney(i.priceAtTime * i.quantity)}</div>`).join("")}
      </div>
      <div class="order-total">合计 ${fmtMoney(order.orderTotalMoney)}</div>
      ${orderActionsHtml(idx)}
    </div>
  `).join("");
}

/* ---------- 订单详情（小票）渲染：下单成功与「补评分」共用 ---------- */
const ORDER_STATUS_TEXT = { pending: "待处理", paid: "已支付", completed: "已完成", cancelled: "已取消" };

function renderOrderDetail(order) {
  $("#orderDetail").innerHTML = `
    <div>订单号　#${order.orderNum}</div>
    <div>时间　　${(order.orderTime || "").replace("T", " ")}</div>
    <div>状态　　${ORDER_STATUS_TEXT[order.orderStatus] || order.orderStatus}</div>
    <div>明细　　${(order.items || []).map((i) => `${esc(i.dishName)} ×${i.quantity}`).join("、")}</div>
    <div class="detail-total">合计　　${fmtMoney(order.orderTotalMoney)}</div>
    <div class="barcode"></div>
  `;
}

/* =========================================================
 * 评分：先选后提交
 * 点星星只是「选中」，随时可改；点「提交评分」才发请求。
 * 提交后仍可改，再次提交只发变动过的那几条。
 * ========================================================= */
const rateState = {
  order: null,
  picked: new Map(),    // dishNumber -> 当前选中的分数（未提交或已改动）
  submitted: new Map(), // dishNumber -> 已成功提交的分数
};

function renderRateList(order) {
  const rateList = $("#rateList");
  rateList.innerHTML = "";
  (order.items || []).forEach((item) => {
    const row = document.createElement("div");
    row.className = "rate-row";
    row.innerHTML = `
      <span>${esc(item.dishName)}</span>
      <span class="rate-right">
        <span class="rate-state"></span>
        <span class="stars">
          ${[1, 2, 3, 4, 5].map((n) => `<button class="star" data-score="${n}" aria-label="评 ${n} 星">★</button>`).join("")}
        </span>
      </span>
    `;
    const stars = row.querySelectorAll(".star");
    const stateEl = row.querySelector(".rate-state");

    // 点亮到第 idx 颗（仅 UI，不发请求）
    const paint = (score) => {
      stars.forEach((s, i) => s.classList.toggle("on", i < score));
    };
    const refreshState = () => {
      const picked = rateState.picked.get(item.dishNumber);
      const sent = rateState.submitted.get(item.dishNumber);
      stateEl.textContent = sent ? (picked && picked !== sent ? `已评${sent}星 → 改为${picked}星` : `已评 ${sent} 星`) : "";
      row.classList.toggle("is-sent", !!sent);
    };

    stars.forEach((s, idx) => {
      s.addEventListener("click", () => {
        const score = idx + 1;
        // 再点一次当前分数 = 取消这一道菜的评分
        if (rateState.picked.get(item.dishNumber) === score) {
          rateState.picked.delete(item.dishNumber);
          const sent = rateState.submitted.get(item.dishNumber);
          paint(sent || 0);
        } else {
          rateState.picked.set(item.dishNumber, score);
          paint(score);
        }
        refreshState();
        updateSubmitBtn();
      });
    });

    const sent = rateState.submitted.get(item.dishNumber) || rateState.picked.get(item.dishNumber);
    paint(sent || 0);
    refreshState();
    rateList.appendChild(row);
  });
  updateSubmitBtn();
}

/** 有待提交的分数就高亮提交按钮 */
function updateSubmitBtn() {
  let pending = 0;
  rateState.picked.forEach((score, dishNumber) => {
    if (rateState.submitted.get(dishNumber) !== score) pending++;
  });
  const btn = $("#rateSubmitBtn");
  btn.disabled = pending === 0;
  btn.textContent = pending ? `提交评分（${pending}）` : "提交评分";
}

/** 提交：只发「和上次提交不同」的分数 */
async function submitRatings() {
  const changes = [];
  rateState.picked.forEach((score, dishNumber) => {
    if (rateState.submitted.get(dishNumber) !== score) changes.push({ dishNumber, score });
  });
  if (changes.length === 0) return;

  const btn = $("#rateSubmitBtn");
  btn.disabled = true;
  btn.textContent = "提交中…";
  let ok = 0;
  try {
    for (const c of changes) {
      await api("/feedback", {
        method: "POST",
        body: JSON.stringify({
          userId: state.user.userId,
          dishNumber: c.dishNumber,
          feedbackScore: c.score,
        }),
      });
      rateState.submitted.set(c.dishNumber, c.score);
      ok++;
    }
    toast(ok ? `已提交 ${ok} 条评分` : "没有需要提交的评分");
  } catch (e) {
    toast("部分评分提交失败，可重试");
  } finally {
    // 重绘一次，刷新"已评 n 星"标记
    if (rateState.order) renderRateList(rateState.order);
    updateSubmitBtn();
  }
}

/**
 * 底部按钮随场景切换
 * guest：游客单没有评分，只留一个「完成」占满整行
 * success：下单成功，左「完成」右「提交评分」
 * rate：历史订单补评分，左「取消」右「提交评分」
 */
function setOrderModalButtons(mode) {
  const close = $("#orderModalCloseBtn");
  const submit = $("#rateSubmitBtn");
  // 必须限定在 #orderModal 内：页面上有三个 .modal-footer，
  // 裸 querySelector 会拿到登录弹窗的那个，游客下单时「完成」就撑不满整行
  const footer = document.querySelector("#orderModal .modal-footer");
  const guest = mode === "guest";
  submit.classList.toggle("hidden", guest);
  footer.classList.toggle("single", guest);
  close.classList.toggle("btn-primary", guest);
  close.classList.toggle("btn-ghost", !guest);
  close.textContent = guest ? "完成" : mode === "rate" ? "取消" : "完成";
}

/* 下单成功：订单详情 + 评分（游客不显示评分） */
function showOrderSuccess(order) {
  $("#orderModalTitle").textContent = "下单成功";
  setOrderModalButtons(state.user ? "success" : "guest");
  $("#rateTip").textContent = "给刚才的菜评个分吧（可选）：";
  rateState.order = order;
  rateState.picked = new Map();
  rateState.submitted = new Map();
  renderOrderDetail(order);

  // 评分接口需要 userId：游客单不显示评分区
  document.querySelector(".rate-section").classList.toggle("hidden", !state.user);

  // 游客单没有账号可挂，只能存本机，并在弹窗里说清楚
  if (!state.user) {
    saveGuestOrder(order);
    $("#orderDetail").insertAdjacentHTML(
      "beforeend",
      '<div class="detail-guest">游客单已保存到本机，可在「我的订单」底部查看</div>'
    );
    renderGuestOrders();
  }

  renderRateList(order);
  openModal("orderModal");
}

/* 历史订单里补评分：复用同一个弹窗 */
function openRateModal(order) {
  if (!state.user) {
    toast("登录后才能评分");
    openModal("loginModal");
    return;
  }
  $("#orderModalTitle").textContent = "给这单打个分";
  setOrderModalButtons("rate"); // 补评分场景：不改分可以直接取消
  $("#rateTip").textContent = "选好分数后点「提交评分」，提交前可随时改：";
  rateState.order = order;
  rateState.picked = new Map();
  rateState.submitted = new Map();
  renderOrderDetail(order);
  document.querySelector(".rate-section").classList.remove("hidden");
  renderRateList(order);
  openModal("orderModal");
}

/* 再来一单：把该单的菜放回购物车 */
function reorder(order) {
  let n = 0;
  (order.items || []).forEach((i) => {
    const dish = {
      dishNumber: i.dishNumber,
      dishName: i.dishName,
      price: i.priceAtTime ?? i.price,
      category: i.category,
    };
    const exist = state.cart.get(dish.dishNumber);
    state.cart.set(dish.dishNumber, {
      dish,
      qty: (exist ? exist.qty : 0) + (i.quantity || 1),
    });
    n++;
  });
  renderCartBar();
  refreshCartUI();
  toast(`已把这单 ${n} 道菜放回购物车`);
}

/* =========================================================
 * 我的订单
 * ========================================================= */
async function loadOrders() {
  renderGuestOrders(); // 本机游客单始终显示（不管有没有登录）
  if (!state.user) {
    $("#orderList").innerHTML = "";
    $("#ordersEmpty").classList.remove("hidden");
    $("#ordersEmptyText").textContent = "登录后就能查看历史订单";
    $("#emptyLoginBtn").classList.remove("hidden");
    return;
  }
  $("#emptyLoginBtn").classList.add("hidden");
  try {
    const data = await api(`/orders/${state.user.userId}?pageNum=1&pageSize=10`, {
      headers: authHeaders(), // GET /orders/{userId} 需要 X-User-Token
    });
    const list = data.list || [];
    const box = $("#orderList");
    box.innerHTML = "";
    $("#ordersEmpty").classList.toggle("hidden", list.length > 0);

    state.serverOrders = list; // 供「再来一单 / 评分」按下标取单
    list.forEach((order, idx) => {
      const card = document.createElement("div");
      card.className = "order-card";
      card.innerHTML = `
        <div class="order-head">
          <span>订单 #${order.orderNum} · ${(order.orderTime || "").replace("T", " ")}</span>
          <span class="order-status status-${esc(order.orderStatus || "")}">${ORDER_STATUS_TEXT[order.orderStatus] || order.orderStatus}</span>
        </div>
        <div class="order-items">
          ${order.items.map((i) => `<div>${esc(i.dishName)} ×${i.quantity}　${fmtMoney(i.priceAtTime * i.quantity)}</div>`).join("")}
        </div>
        <div class="order-total">合计 ${fmtMoney(order.orderTotalMoney)}</div>
        <div class="barcode"></div>
        ${orderActionsHtml(idx)}
      `;
      box.appendChild(card);
    });
  } catch (e) { /* msg 已提示 */ }
}

/* ---------- 订单卡片上的「再来一单 / 评分」：事件委托 ---------- */
function bindOrderActions(el, getList) {
  el.addEventListener("click", (e) => {
    const btn = e.target.closest("[data-action]");
    if (!btn) return;
    const order = getList()[Number(btn.dataset.index)];
    if (!order) return;
    if (btn.dataset.action === "reorder") reorder(order);
    else openRateModal(order);
  });
}
bindOrderActions($("#orderList"), () => state.serverOrders);
bindOrderActions($("#guestOrderList"), () => loadGuestOrders());

/* =========================================================
 * 订单状态自动刷新（停在订单页时轮询，离开即停）
 * ========================================================= */
let orderPollTimer = null;
function stopOrderPolling() {
  if (orderPollTimer) { clearInterval(orderPollTimer); orderPollTimer = null; }
}
function startOrderPolling() {
  stopOrderPolling();
  if (!state.user) return; // 游客单不存在服务端，无需轮询
  orderPollTimer = setInterval(() => {
    if (state.page === "orders" && !document.hidden) loadOrders();
  }, 20000);
}

/* ---------- 弹窗关闭 ---------- */
document.querySelectorAll("[data-close]").forEach((btn) => {
  btn.addEventListener("click", () => closeModal(btn.dataset.close));
});
document.querySelectorAll(".modal-mask").forEach((mask) => {
  mask.addEventListener("click", (e) => {
    if (e.target === mask) closeModal(mask.id); // 点遮罩空白处关闭
  });
});
// 评分提交按钮
$("#rateSubmitBtn").addEventListener("click", submitRatings);

// 订单空态里的「去登录」
$("#emptyLoginBtn").addEventListener("click", () => {
  openModal("loginModal");
  setTimeout(() => $("#loginPhone").focus(), 100);
});

// Esc 关闭所有弹窗
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape") {
    document.querySelectorAll(".modal-mask:not(.hidden)").forEach((m) => closeModal(m.id));
  }
});

/* ---------- 初始化 ---------- */
// 本地缓存里没有 token 的旧会话（token 机制上线前存的）视为已失效
if (state.user && !state.user.token) {
  state.user = null;
  localStorage.removeItem("ordering_user");
}
renderLoginBtn();
renderCartBar();
switchPage("home");
