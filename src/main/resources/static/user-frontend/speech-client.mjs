export const MAX_SPEECH_BYTES = 5 * 1024 * 1024;
const extensions = { "audio/webm": "webm", "audio/ogg": "ogg", "audio/wav": "wav", "audio/mpeg": "mp3" };

export class SpeechRecognitionError extends Error {
  constructor(message, { status = 0, code = null, cause } = {}) {
    super(message, cause === undefined ? undefined : { cause });
    this.name = "SpeechRecognitionError";
    this.status = status;
    this.code = code;
  }
}

function responseMessage(body, fallback) {
  return typeof body?.msg === "string" && body.msg.trim() && body.msg.length <= 300 && !/[<>]/.test(body.msg)
    ? body.msg : fallback;
}

/** Upload once; the browser supplies the multipart Content-Type and boundary. */
export async function recognizeSpeech({ audio, token, baseUrl = "", signal, fetchImpl = fetch }) {
  if (typeof token !== "string" || !token.trim()) {
    throw new SpeechRecognitionError("请先登录后再使用语音输入", { code: 401 });
  }
  if (!(audio instanceof Blob) || audio.size === 0) {
    throw new SpeechRecognitionError("没有录到有效音频，请重新录音", { code: 400 });
  }
  if (audio.size > MAX_SPEECH_BYTES) {
    throw new SpeechRecognitionError("录音不能超过5MB，请缩短后重试", { code: 413 });
  }
  const type = audio.type.split(";", 1)[0].trim().toLowerCase();
  if (!extensions[type]) {
    throw new SpeechRecognitionError("当前录音格式不支持，请使用支持WebM或Ogg录音的浏览器", { code: 400 });
  }
  const form = new FormData();
  form.append("audio", audio, `recording.${extensions[type]}`);
  let response;
  try {
    response = await fetchImpl(`${baseUrl}/speech/recognize`, {
      method: "POST", credentials: "same-origin", signal,
      headers: { Accept: "application/json", "X-User-Token": token }, body: form,
    });
  } catch (cause) {
    if (signal?.aborted || cause?.name === "AbortError") throw cause;
    throw new SpeechRecognitionError("语音识别连接中断，请稍后重试", { cause });
  }
  let body;
  try { body = await response.json(); }
  catch (cause) {
    if (signal?.aborted || cause?.name === "AbortError") throw cause;
    throw new SpeechRecognitionError("语音识别返回异常，请稍后重试", { status: response.status, cause });
  }
  if (!response.ok || body?.code !== 0) {
    throw new SpeechRecognitionError(responseMessage(body, "暂时无法识别语音，请稍后重试"), {
      status: response.status, code: Number.isInteger(body?.code) ? body.code : null,
    });
  }
  if (typeof body.data?.text !== "string" || !body.data.text.trim()) {
    throw new SpeechRecognitionError("未识别到有效文字，请重新录音", { status: response.status, code: 400 });
  }
  return body.data.text.trim();
}
