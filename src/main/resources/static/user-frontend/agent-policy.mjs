export function canConfirmDraft(result, now = Date.now()) {
  return result?.status === "SUCCESS" && typeof result.draftId === "string" && !!result.draftId
    && Array.isArray(result.draft?.items) && result.draft.items.length > 0
    && result.draft.overBudget === false
    && result.draft.totalAmount != null && Number(result.draft.totalAmount) >= 0
    && Number.isFinite(Number(result.draft.totalAmount))
    && Number.isFinite(Date.parse(result.expireAt)) && Date.parse(result.expireAt) > now;
}

export function confirmedDraftRequest(result, now = Date.now()) {
  if (!canConfirmDraft(result, now)) throw new Error("这份报价已无法确认，请重新试算");
  return { message: `我已核对这份报价，确认提交草稿 ${result.draftId}。请只提交这份草稿，不修改方案，也不要新建草稿。`,
    confirmedDraftId: result.draftId };
}

export function candidatePayload(result) {
  const candidate = result?.proposal;
  if (result?.status !== "SUCCESS" || !candidate
      || ![null, "none", "mild", "medium", "high"].includes(candidate.spicyLevel)
      || !(candidate.preferenceText === null || typeof candidate.preferenceText === "string")) {
    throw new Error("候选信息不完整，请重新整理口味");
  }
  // PUT replaces the whole profile. Always send both fields, including explicit null.
  return { spicyLevel: candidate.spicyLevel, preferenceText: candidate.preferenceText };
}
