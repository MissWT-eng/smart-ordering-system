package com.luwei.ordering.controller;

import com.luwei.ordering.agent.OrderingAgent;
import com.luwei.ordering.common.exception.BusinessException;
import com.luwei.ordering.common.exception.ErrorCode;
import com.luwei.ordering.dto.request.AgentChatRequest;
import com.luwei.ordering.dto.response.AgentChatResponse;
import com.luwei.ordering.dto.response.ApiResponse;
import com.luwei.ordering.service.ConversationMemoryService;
import com.luwei.ordering.service.ConversationService;
import com.luwei.ordering.service.TokenService;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
@Slf4j
public class AgentChatController {
    private final OrderingAgent orderingAgent;
    private final HttpServletRequest httpServletRequest;
    private final TokenService tokenService;
    private final ConversationService conversationService;
    private final ConversationMemoryService conversationMemoryService;

    @PostMapping("/chat")
    public ApiResponse<AgentChatResponse> chat(@RequestBody @Valid AgentChatRequest req){
        String token = httpServletRequest.getHeader("X-User-Token");
        String ownerKey;
        if(token == null){
            // 游客:取得现有对话的身份
            ownerKey = "guest" + httpServletRequest.getSession().getId();
        }
        else {
            // 注册用户：先验证 token，再得到真实用户编号
            Long userId = tokenService.resolve(token);

            if (userId == null) {
                throw new BusinessException(ErrorCode.NOT_AUTH);
            }

            ownerKey = "user:" + userId;
        }

        InvocationParameters parameters = InvocationParameters.from("ownerKey", ownerKey);

        String confirmedDraftId = req.getConfirmedDraftId();

        if (confirmedDraftId != null) {
            if (confirmedDraftId.isBlank()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR);
            }

            parameters.put("confirmedDraftId", confirmedDraftId);
        }

        log.info("agent chat, len={}", req.getMessage().length());

        String conversationId = conversationService.resolveConversationId(
                req.getConversationId(), ownerKey);

        conversationMemoryService.beginUse(conversationId);

        try{
            Result<String> result = orderingAgent.chat(req.getMessage(), parameters, conversationId);
            log.info("当前Agent工具调用次数:{}", result.toolExecutions().size());
            for (ToolExecution execution : result.toolExecutions()) {
                log.info("Agent调用工具:{}", execution.request().name());
                log.debug("Agent工具调用详情: tool={}, arguments={}, result={}",
                        execution.request().name(),
                        execution.request().arguments(),
                        execution.result());
            }

            return ApiResponse.success(
                    new AgentChatResponse(conversationId, result.content()));

        }

        catch (Exception e){log.warn("当前调用agent出现问题", e);}

        finally {
            conversationMemoryService.endUse(conversationId);
        }

        return ApiResponse.error(ErrorCode.LLM_SERVICE_UNAVAILABLE);
    }

}
