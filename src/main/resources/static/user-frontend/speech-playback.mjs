import { buildSpeechText, MAX_SPEECH_TEXT_LENGTH } from "./speech-text.mjs?v=20261103-1";
import { synthesizeSpeech, downloadSpeechAudio } from "./speech-synthesis-client.mjs?v=20261103-1";

/** One player per page. A completed audio Blob is reused until its conversation is cleared. */
export function createSpeechPlayback({ ui, isRecording }) {
  const entries = new Set();
  let current = null;
  function sameOwner(operation) {
    const user = ui.getUser();
    return user?.userId === operation.userId && user?.token === operation.token;
  }
  function currentRequest(entry, request) { return entry.request === request && current === entry && sameOwner(request); }
  function show(entry, message = "", error = false) {
    entry.status.textContent = message;
    entry.status.classList.toggle("is-error", error);
  }
  function syncEntry(entry) {
    entry.button.disabled = !entry.text || entry.text.length > MAX_SPEECH_TEXT_LENGTH || !!isRecording();
    const label = entry.request ? "取消生成"
      : entry.playing ? "停止朗读" : entry.audio ? "重新播放" : "朗读回复";
    entry.button.querySelector("span").textContent = label;
    entry.button.setAttribute("aria-label", label);
    entry.button.setAttribute("aria-pressed", String(entry.playing));
  }
  function stop({ clearCache = false, forgetEntries = false } = {}) {
    if (current) {
      const entry = current;
      entry.playEpoch++;
      current = null;
      const request = entry.request;
      entry.request = null;
      if (request) { clearTimeout(request.timeout); request.controller.abort(); }
      if (entry.audio) { entry.audio.pause(); entry.audio.currentTime = 0; }
      entry.playing = false;
      show(entry, request ? "已取消生成。" : "已停止朗读。");
      syncEntry(entry);
    }
    if (clearCache) {
      for (const entry of entries) {
        if (entry.audio) { entry.audio.pause(); entry.audio.removeAttribute("src"); entry.audio.load(); }
        if (entry.objectUrl) URL.revokeObjectURL(entry.objectUrl);
        entry.audio = null;
        entry.objectUrl = null;
        entry.synthesized = null;
        syncEntry(entry);
      }
      if (forgetEntries) entries.clear();
    }
  }
  async function play(entry) {
    if (current !== entry || isRecording()) return;
    const epoch = ++entry.playEpoch;
    const audio = entry.audio;
    try {
      audio.currentTime = 0;
      await audio.play();
      if (entry.playEpoch !== epoch) return;
      if (current !== entry) { audio.pause(); return; }
      entry.playing = true;
      show(entry, "正在朗读。");
    } catch (error) {
      if (current !== entry || entry.playEpoch !== epoch) return;
      entry.playing = false;
      current = null;
      show(entry, error.name === "NotAllowedError" ? "音频已准备好，请再点击播放。"
        : "音频暂时无法播放，可以再次点击重试。", error.name !== "NotAllowedError");
    }
    syncEntry(entry);
  }
  function retainAudio(entry, blob) {
    entry.objectUrl = URL.createObjectURL(blob);
    const audio = new Audio(entry.objectUrl);
    audio.preload = "auto";
    audio.addEventListener("ended", () => {
      if (current !== entry) return;
      current = null;
      entry.playing = false;
      show(entry, "朗读结束，可再次播放。");
      syncEntry(entry);
    });
    audio.addEventListener("error", () => {
      if (current !== entry) return;
      audio.pause();
      current = null;
      entry.playing = false;
      show(entry, "音频暂时无法播放，可以再次点击重试。", true);
      syncEntry(entry);
    });
    entry.audio = audio;
  }
  async function activate(entry) {
    if (entry.button.disabled || !entry.button.isConnected || isRecording()) return;
    const user = ui.getUser();
    if (!user?.token) { ui.openLogin(); return; }
    if (current === entry) { stop(); return; }
    stop();
    current = entry;
    if (entry.audio) { await play(entry); return; }
    const request = { controller: new AbortController(), userId: user.userId, token: user.token, timedOut: false };
    entry.request = request;
    request.timeout = setTimeout(() => { request.timedOut = true; request.controller.abort(); }, 45_000);
    show(entry, entry.synthesized ? "正在下载音频……" : "正在生成安欢的朗读……");
    syncEntry(entry);
    try {
      // Keep the signed URL after a download failure so another click does not synthesize again.
      if (!entry.synthesized) {
        const result = await synthesizeSpeech({ text: entry.text, token: request.token,
          baseUrl: ui.getApiBase(), signal: request.controller.signal });
        if (!currentRequest(entry, request)) return;
        entry.synthesized = result;
      }
      const blob = await downloadSpeechAudio({ audioUrl: entry.synthesized.audioUrl, signal: request.controller.signal });
      if (!currentRequest(entry, request)) return;
      retainAudio(entry, blob);
      clearTimeout(request.timeout);
      entry.request = null;
      await play(entry);
    } catch (error) {
      if (!currentRequest(entry, request)) return;
      entry.request = null;
      current = null;
      show(entry, request.timedOut ? "朗读请求超时，请稍后重试。"
        : error.message || "朗读服务暂不可用，请稍后重试。", true);
      if ((error.status === 401 || error.code === 401) && sameOwner(request)) ui.handleAuthExpired();
    } finally {
      clearTimeout(request.timeout);
      if (entry.request === request) entry.request = null;
      if (entry.button.isConnected) syncEntry(entry);
    }
  }
  function attach(actions, data) {
    const text = buildSpeechText(data);
    const button = document.createElement("button");
    button.type = "button";
    button.className = "read-reply";
    button.setAttribute("aria-label", "朗读这条已完成的回复");
    button.setAttribute("aria-pressed", "false");
    button.innerHTML = '<svg class="icon" aria-hidden="true"><use href="#i-volume"/></svg><span>朗读回复</span>';
    const status = document.createElement("span");
    status.className = "speech-playback-status";
    status.setAttribute("role", "status");
    status.setAttribute("aria-live", "polite");
    const entry = { text, button, status, request: null, audio: null, objectUrl: null, playing: false, synthesized: null, playEpoch: 0 };
    entries.add(entry);
    if (!text) show(entry, "这条回复没有适合朗读的文字。");
    else if (text.length > MAX_SPEECH_TEXT_LENGTH) show(entry, "这条回复较长，暂不提供朗读。");
    button.addEventListener("click", () => { void activate(entry); });
    actions.append(button, status);
    syncEntry(entry);
  }
  window.addEventListener("pagehide", () => stop());
  document.addEventListener("visibilitychange", () => { if (document.hidden) stop(); });
  return { attach, stop, updateForArticle(article, data) {
    const matches = [...entries].filter((entry) => entry.button.closest(".chat-message") === article);
    if (!matches.length) {
      let actions = article.querySelector(".message-tools");
      if (!actions) { actions = document.createElement("div"); actions.className = "message-tools"; article.append(actions); }
      attach(actions, data);
      return;
    }
    for (const entry of matches) {
      if (current === entry) stop();
      if (entry.audio) { entry.audio.pause(); entry.audio.removeAttribute("src"); entry.audio.load(); }
      if (entry.objectUrl) URL.revokeObjectURL(entry.objectUrl);
      entry.audio = null;
      entry.objectUrl = null;
      entry.synthesized = null;
      entry.text = buildSpeechText(data);
      show(entry, "已按核实结果更新朗读内容。");
      syncEntry(entry);
    }
  }, syncControls() {
    if (isRecording() && current) stop();
    for (const entry of entries) syncEntry(entry);
  } };
}
