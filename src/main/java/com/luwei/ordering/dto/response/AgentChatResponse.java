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
    //当前模型对长期口味的工具返回结果
    private UserPreferenceProposalToolResult proposalToolResult;
    // 本轮实际试算与确认结果，供前端展示和明确确认，不解析模型文本。
    private DraftOrderToolResult draftToolResult;
    private ConfirmOrderToolResult orderToolResult;

    public AgentChatResponse(String conversationId, String reply,
                             UserPreferenceProposalToolResult proposalToolResult) {
        this(conversationId, reply, proposalToolResult, null, null);
    }
}
