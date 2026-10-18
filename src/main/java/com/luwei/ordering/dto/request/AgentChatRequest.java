package com.luwei.ordering.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AgentChatRequest {
    // 用户明确确认提交的草稿编号，普通聊天不传
    private String confirmedDraftId;

    //对话Id，同一个用户可以有多段对话(可选)
    private String conversationId;

    @NotBlank
    private String message;
}
