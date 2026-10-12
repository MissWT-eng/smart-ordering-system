package com.luwei.ordering.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AgentChatRequest {
    // 用户明确确认提交的草稿编号，普通聊天不传
    private String confirmedDraftId;

    @NotBlank
    private String message;
}
