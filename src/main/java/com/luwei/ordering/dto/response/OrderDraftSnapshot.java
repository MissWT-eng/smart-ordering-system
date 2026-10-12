package com.luwei.ordering.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * draftId：后端生成的唯一编号。
 * ownerKey：草稿属于哪个用户或游客会话。
 * quote：现有的 DraftOrderResponse，包含明细、金额和预算。
 * createdAt、expiresAt：创建时间和失效时间。
 * */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderDraftSnapshot {
    private String draftId;
    private String ownerKey;
    private DraftOrderResponse quote;
    private LocalDateTime createdAt;
    private LocalDateTime expireAt;
}
