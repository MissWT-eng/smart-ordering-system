import { MAX_SPEECH_TEXT_LENGTH } from "./speech-text.mjs?v=20261103-1";

export class SpeechSynthesisError extends Error {
  constructor(message, { status = 0, code = null, cause } = {}) {
    super(message, cause === undefined ? undefined : { cause });
    this.name = "SpeechSynthesisError";
    this.status = status;
    this.code = code;
  }
}

export async function synthesizeSpeech({ text, token, baseUrl = "", signal, fetchImpl = fetch }) {
  if (typeof token !== "string" || !token.trim()) throw new SpeechSynthesisError("请先登录后再朗读", { code: 401 });
  if (typeof text !== "string" || !text.trim() || text.length > MAX_SPEECH_TEXT_LENGTH) {
    throw new SpeechSynthesisError("朗读内容须为1至1000字", { code: 400 });
  }
  let response, body;
  try {
    response = await fetchImpl(`${baseUrl}/speech/synthesize`, {
      method: "POST", credentials: "same-origin", signal,
      headers: { "Content-Type": "application/json", Accept: "application/json", "X-User-Token": token },
      body: JSON.stringify({ text }),
    });
    body = await response.json();
  } catch (cause) {
    if (signal?.aborted || cause?.name === "AbortError") throw cause;
    throw new SpeechSynthesisError("朗读请求未完成，请稍后重试", { status: response?.status, cause });
  }
  if (!response.ok || body?.code !== 0) {
    const message = typeof body?.msg === "string" && body.msg.trim() && body.msg.length <= 300 && !/[<>]/.test(body.msg)
      ? body.msg : "朗读服务暂不可用，请稍后重试";
    throw new SpeechSynthesisError(message, { status: response.status, code: body?.code });
  }
  let url;
  try { url = new URL(body?.data?.audioUrl); } catch { /* Handled below. */ }
  if (!url || url.protocol !== "https:" || url.username || url.password || url.hash
      || !body.data.model || !body.data.voice) throw new SpeechSynthesisError("朗读服务返回的音频信息不完整");
  return { audioUrl: url.href, model: body.data.model, voice: body.data.voice };
}

/** Signed audio downloads carry no application credentials. */
export async function downloadSpeechAudio({ audioUrl, signal, fetchImpl = fetch }) {
  let response;
  try {
    response = await fetchImpl(audioUrl, { signal, credentials: "omit", referrerPolicy: "no-referrer" });
    if (!response.ok) throw new SpeechSynthesisError("音频下载失败，请稍后重试");
    const type = response.headers.get("Content-Type")?.split(";", 1)[0].trim().toLowerCase();
    if (!type?.startsWith("audio/") && type !== "application/octet-stream") {
      throw new SpeechSynthesisError("音频格式不正确，请稍后重试");
    }
    const blob = await response.blob();
    if (!blob.size || blob.size > 10 * 1024 * 1024) throw new SpeechSynthesisError("音频文件为空或过大");
    return blob;
  } catch (cause) {
    if (signal?.aborted || cause?.name === "AbortError" || cause instanceof SpeechSynthesisError) throw cause;
    throw new SpeechSynthesisError("音频未能下载，请稍后重试", { cause });
  }
}
