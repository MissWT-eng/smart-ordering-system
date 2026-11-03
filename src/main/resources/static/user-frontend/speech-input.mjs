import { recognizeSpeech } from "./speech-client.mjs?v=20261103-1";
import { createSpeechRecorder } from "./speech-recorder.mjs?v=20261103-1";

const joinText = (original, transcript) => original.trim()
  ? `${original}${/\s$/.test(original) ? "" : "\n"}${transcript}` : transcript;

/** Connect recording to the composer; speech never submits an Agent message. */
export function createSpeechInput({ ui, input, isBlocked, getConversationEpoch, onChange, beforeRecording = () => {} }) {
  const record = document.querySelector("#recordSpeech");
  const cancelButton = document.querySelector("#cancelSpeech");
  const status = document.querySelector("#speechStatus");
  const previewPanel = document.querySelector("#speechTranscriptPanel");
  const previewText = document.querySelector("#speechTranscriptText");
  const insertButton = document.querySelector("#insertSpeechTranscript");
  const discardButton = document.querySelector("#discardSpeechTranscript");
  let phase = "idle", active = null, preview = null, inputRevision = 0;

  function showStatus(message, error = false) {
    status.textContent = message;
    status.classList.toggle("is-error", error);
  }
  function sameOwner(operation) {
    const user = ui.getUser();
    return operation.epoch === getConversationEpoch()
      && operation.userId === user?.userId && operation.token === user?.token;
  }
  function isCurrent(operation) { return active === operation && sameOwner(operation); }
  function clearPreview() {
    preview = null;
    previewText.textContent = "";
    previewPanel.classList.add("hidden");
  }
  function syncControls() {
    record.disabled = isBlocked() || ["requesting", "stopping", "transcribing"].includes(phase);
    const labels = { idle: "语音输入", requesting: "等待麦克风", recording: "停止并识别",
      stopping: "正在结束", transcribing: "正在识别" };
    record.querySelector("span").textContent = labels[phase];
    record.setAttribute("aria-pressed", String(phase === "recording"));
    record.classList.toggle("is-recording", phase === "recording");
    cancelButton.classList.toggle("hidden", phase === "idle");
    cancelButton.textContent = phase === "transcribing" ? "取消识别" : "取消录音";
    insertButton.disabled = !preview || !sameOwner(preview) || phase !== "idle" || isBlocked()
      || joinText(input.value, preview.text).length > input.maxLength;
    previewPanel.setAttribute("aria-busy", String(phase === "transcribing"));
  }
  function refresh() { syncControls(); onChange(); }
  function cancel(message = "") {
    // Invalidate first: a late permission grant or network reply belongs to the old operation.
    const previous = active;
    active = null;
    if (previous) {
      clearTimeout(previous.timeout);
      previous.controller?.abort();
    }
    recorder.cancel();
    phase = "idle";
    clearPreview();
    showStatus(message);
    refresh();
  }
  function insertText(text) {
    input.value = joinText(input.value, text);
    input.dispatchEvent(new Event("input", { bubbles: true }));
    input.focus();
  }
  const recorder = createSpeechRecorder({
    onState(next) {
      phase = next;
      if (next === "requesting") showStatus("请允许使用麦克风，也可以取消后继续打字。");
      if (next === "recording") showStatus("录音中，点击停止并识别；最长60秒。");
      if (next === "stopping") showStatus("正在结束录音……");
      refresh();
    },
    onError(error) {
      active = null;
      phase = "idle";
      showStatus(error.message, true);
      refresh();
    },
    onAudio(audio) { void transcribe(audio); },
  });

  async function transcribe(audio) {
    const operation = active;
    if (!operation || !isCurrent(operation)) { cancel(); return; }
    operation.controller = new AbortController();
    operation.timedOut = false;
    operation.timeout = setTimeout(() => {
      operation.timedOut = true;
      operation.controller.abort();
    }, 45_000);
    phase = "transcribing";
    showStatus("正在识别，完成后请检查文字再发送。");
    refresh();
    try {
      const text = await recognizeSpeech({ audio, token: operation.token, baseUrl: ui.getApiBase(),
        signal: operation.controller.signal });
      if (!isCurrent(operation)) return;
      const combined = joinText(input.value, text);
      if (inputRevision !== operation.inputRevision || input.value !== operation.originalText
          || combined.length > input.maxLength) {
        preview = { ...operation, text };
        previewText.textContent = text;
        previewPanel.classList.remove("hidden");
        showStatus(combined.length > input.maxLength
          ? "合并后超过2000字，已保留完整识别结果。请先精简输入，或选中复制需要的内容。"
          : "输入框内容有变化，识别结果已单独保留。你可以检查后插入。");
      } else {
        insertText(text);
        showStatus("文字已填入输入框，请检查后再发送。");
      }
    } catch (error) {
      if (!isCurrent(operation)) return;
      showStatus(operation.timedOut ? "语音识别超时，请稍后重试。输入框原文已保留。"
        : error.message || "语音识别失败，请重试或继续打字。", true);
      if ((error.status === 401 || error.code === 401) && ui.getUser()?.token === operation.token) {
        ui.handleAuthExpired();
      }
    } finally {
      clearTimeout(operation.timeout);
      if (active === operation) {
        active = null;
        phase = "idle";
        refresh();
      }
    }
  }

  record.addEventListener("click", () => {
    if (phase === "recording") { recorder.stop(); return; }
    if (phase !== "idle" || isBlocked()) return;
    const user = ui.getUser();
    if (!user?.token) { showStatus("登录后可以使用语音输入。"); ui.openLogin(); return; }
    beforeRecording();
    clearPreview();
    active = { userId: user.userId, token: user.token, epoch: getConversationEpoch(),
      inputRevision, originalText: input.value, controller: null, timeout: null };
    void recorder.start();
  });
  cancelButton.addEventListener("click", () => cancel("已取消，输入框原文已保留。"));
  insertButton.addEventListener("click", () => {
    if (!preview || !sameOwner(preview) || phase !== "idle" || isBlocked()) return;
    if (joinText(input.value, preview.text).length > input.maxLength) {
      showStatus("合并后超过2000字，请先精简输入或选中复制需要的内容。", true);
      return;
    }
    const text = preview.text;
    clearPreview();
    insertText(text);
    showStatus("文字已插入，请检查后再发送。");
    refresh();
  });
  discardButton.addEventListener("click", () => { clearPreview(); showStatus("已丢弃识别结果。"); refresh(); });
  window.addEventListener("pagehide", () => cancel());
  document.addEventListener("visibilitychange", () => {
    if (document.hidden && phase !== "idle") cancel();
  });
  syncControls();
  return { isBusy: () => phase !== "idle", cancel, syncControls,
    inputChanged: () => { inputRevision++; syncControls(); } };
}
