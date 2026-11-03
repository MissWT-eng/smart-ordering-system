const RECORDING_TYPES = [
  "audio/webm;codecs=opus",
  "audio/webm",
  "audio/ogg;codecs=opus",
  "audio/ogg",
];

export class SpeechRecordingError extends Error {
  constructor(message, { code = "RECORDING_FAILED", cause } = {}) {
    super(message, cause === undefined ? undefined : { cause });
    this.name = "SpeechRecordingError";
    this.code = code;
  }
}

function permissionError(cause) {
  const messages = {
    NotAllowedError: ["MICROPHONE_DENIED", "未获得麦克风权限，请允许录音或继续打字"],
    SecurityError: ["MICROPHONE_DENIED", "当前页面无法使用麦克风，请继续打字"],
    NotFoundError: ["MICROPHONE_NOT_FOUND", "没有找到可用的麦克风，请检查设备或继续打字"],
    NotReadableError: ["MICROPHONE_UNAVAILABLE", "麦克风暂时无法使用，请检查设备后重试"],
  };
  const [code, message] = messages[cause?.name] || ["RECORDING_FAILED", "录音未能开始，请稍后重试或继续打字"];
  return new SpeechRecordingError(message, { code, cause });
}

function releaseTracks(stream) {
  for (const track of stream?.getTracks() || []) {
    try { track.stop(); } catch { /* Release the remaining tracks even if one device has gone away. */ }
  }
}

/** Owns one microphone session. Uploading, identity and input text belong to the caller. */
export function createSpeechRecorder({
  onState = () => {},
  onAudio = () => {},
  onError = () => {},
  maxDurationMs = 60_000,
  maxBytes = 5 * 1024 * 1024,
  mediaDevices = globalThis.navigator?.mediaDevices,
  MediaRecorderClass = globalThis.MediaRecorder,
  setTimeoutImpl = globalThis.setTimeout,
  clearTimeoutImpl = globalThis.clearTimeout,
} = {}) {
  if (!Number.isFinite(maxDurationMs) || maxDurationMs <= 0 || !Number.isSafeInteger(maxBytes) || maxBytes <= 0) {
    throw new RangeError("录音时长和大小限制必须大于零");
  }
  let state = "idle";
  let active = null;

  function setState(next) {
    if (state === next) return;
    state = next;
    onState(next);
  }
  function isCurrent(session) { return active === session; }
  function clearTimer(session) {
    if (session.timer !== null) clearTimeoutImpl(session.timer);
    session.timer = null;
  }
  function dispose(session) {
    clearTimer(session);
    for (const { track, listener } of session.trackListeners) track.removeEventListener("ended", listener);
    session.trackListeners = [];
    const recorder = session.recorder;
    if (recorder) {
      recorder.ondataavailable = null;
      recorder.onstop = null;
      recorder.onerror = null;
      if (recorder.state !== "inactive") {
        try { recorder.stop(); } catch { /* Cancellation must still release the microphone. */ }
      }
    }
    releaseTracks(session.stream);
    session.chunks = [];
    session.stream = null;
    session.recorder = null;
  }
  function fail(session, error) {
    if (!isCurrent(session)) return;
    active = null;
    dispose(session);
    setState("idle");
    onError(error);
  }
  function finish(session) {
    if (!isCurrent(session)) return;
    if (!session.stopRequested) {
      fail(session, new SpeechRecordingError("录音已意外中断，请重新录音", { code: "RECORDING_INTERRUPTED" }));
      return;
    }
    if (!session.bytes) {
      fail(session, new SpeechRecordingError("没有录到有效音频，请重新录音", { code: "EMPTY_AUDIO" }));
      return;
    }
    // The stop event follows the final dataavailable event. Build the Blob before releasing tracks.
    const audio = new Blob(session.chunks, { type: session.mimeType });
    active = null;
    dispose(session);
    setState("idle");
    onAudio(audio);
  }

  async function start() {
    if (state !== "idle") return false;
    if (!mediaDevices?.getUserMedia || typeof MediaRecorderClass !== "function") {
      onError(new SpeechRecordingError("当前浏览器或页面不支持录音，请使用 localhost、HTTPS 或继续打字", { code: "RECORDING_UNSUPPORTED" }));
      return false;
    }
    const mimeType = RECORDING_TYPES.find((type) => MediaRecorderClass.isTypeSupported?.(type));
    if (!mimeType) {
      onError(new SpeechRecordingError("当前浏览器不支持可识别的录音格式，请换用其他浏览器或继续打字", { code: "AUDIO_TYPE_UNSUPPORTED" }));
      return false;
    }
    const session = { stream: null, recorder: null, chunks: [], bytes: 0, mimeType,
      timer: null, trackListeners: [], stopRequested: false };
    active = session;
    setState("requesting");
    try {
      if (!isCurrent(session)) return false;
      const stream = await mediaDevices.getUserMedia({ audio: true });
      // A pending browser permission prompt cannot be aborted. Discard its late result after cancel.
      if (!isCurrent(session)) {
        releaseTracks(stream);
        return false;
      }
      session.stream = stream;
      const recorder = new MediaRecorderClass(stream, { mimeType });
      session.recorder = recorder;
      session.mimeType = recorder.mimeType || mimeType;
      const actualType = session.mimeType.split(";", 1)[0].trim().toLowerCase();
      if (!["audio/webm", "audio/ogg"].includes(actualType)) {
        throw new SpeechRecordingError("浏览器输出的音频格式暂不支持识别，请继续打字", { code: "AUDIO_TYPE_UNSUPPORTED" });
      }
      recorder.ondataavailable = (event) => {
        if (!isCurrent(session) || !event.data?.size) return;
        const chunkType = event.data.type || recorder.mimeType || session.mimeType;
        const chunkBaseType = chunkType.split(";", 1)[0].trim().toLowerCase();
        if (!["audio/webm", "audio/ogg"].includes(chunkBaseType)) {
          fail(session, new SpeechRecordingError("浏览器输出的音频格式暂不支持识别，请继续打字", { code: "AUDIO_TYPE_UNSUPPORTED" }));
          return;
        }
        session.mimeType = chunkType;
        session.bytes += event.data.size;
        if (session.bytes > maxBytes) {
          fail(session, new SpeechRecordingError("录音文件过大，请缩短后重新录音", { code: "AUDIO_TOO_LARGE" }));
          return;
        }
        session.chunks.push(event.data);
      };
      recorder.onstop = () => finish(session);
      recorder.onerror = (event) => fail(session,
        new SpeechRecordingError("录音出现错误，请重新录音或继续打字", { code: "RECORDING_FAILED", cause: event.error }));
      for (const track of stream.getTracks()) {
        const listener = () => fail(session,
          new SpeechRecordingError("麦克风连接已中断，请检查设备后重试", { code: "RECORDING_INTERRUPTED" }));
        track.addEventListener("ended", listener);
        session.trackListeners.push({ track, listener });
      }
      recorder.start(1000);
      setState("recording");
      if (isCurrent(session) && state === "recording") session.timer = setTimeoutImpl(stop, maxDurationMs);
      return isCurrent(session);
    } catch (cause) {
      if (isCurrent(session)) fail(session, cause instanceof SpeechRecordingError ? cause : permissionError(cause));
      return false;
    }
  }

  function stop() {
    const session = active;
    if (!session || state !== "recording") return false;
    session.stopRequested = true;
    clearTimer(session);
    setState("stopping");
    try {
      if (session.recorder.state !== "inactive") session.recorder.stop();
      // Keep the tracks alive until onstop so the last audio fragment is included.
      return true;
    } catch (cause) {
      fail(session, new SpeechRecordingError("录音未能正常结束，请重新录音", { code: "RECORDING_FAILED", cause }));
      return false;
    }
  }

  function cancel() {
    const session = active;
    if (!session) return false;
    active = null;
    dispose(session);
    setState("idle");
    return true;
  }

  return { start, stop, cancel, getState: () => state };
}
