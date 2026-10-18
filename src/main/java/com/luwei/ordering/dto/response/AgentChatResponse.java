package com.luwei.ordering.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentChatResponse {
    //当前对话Id
    private String conversationId;
    //当前LLM回答
    private String reply;
}
