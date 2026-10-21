package com.luwei.ordering.controller;

import com.luwei.ordering.agent.OrderingAgent;
import com.luwei.ordering.agent.stream.AgentChatStreamSession;
import com.luwei.ordering.common.exception.BusinessException;
import com.luwei.ordering.common.exception.ErrorCode;
import com.luwei.ordering.concurrent.AgentChatConcurrencyLimiter;
import com.luwei.ordering.dto.request.AgentChatRequest;
import com.luwei.ordering.dto.response.AgentChatResponse;
import com.luwei.ordering.dto.response.ApiResponse;
import com.luwei.ordering.dto.response.ConfirmOrderToolResult;
import com.luwei.ordering.dto.response.DraftOrderToolResult;
import com.luwei.ordering.dto.response.UserPreferenceDTO;
import com.luwei.ordering.dto.response.UserPreferenceProposalToolResult;
import com.luwei.ordering.service.ConversationMemoryService;
import com.luwei.ordering.service.ConversationService;
import com.luwei.ordering.service.TokenService;
import com.luwei.ordering.service.UserPreferenceService;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private final UserPreferenceService userPreferenceService;
    private final ObjectMapper objectMapper;
    private final AgentChatConcurrencyLimiter concurrencyLimiter;

    @PostMapping("/chat")
    public ApiResponse<AgentChatResponse> chat(@RequestBody @Valid AgentChatRequest req){
        String token = httpServletRequest.getHeader("X-User-Token");
        String ownerKey;
        Long userId = null;
        UserPreferenceDTO userPreference = null;
        if(token == null){
            // 游客:取得现有对话的身份
            ownerKey = "guest" + httpServletRequest.getSession().getId();
        }
        else {
            // 注册用户：先验证 token，再得到真实用户编号
             userId = tokenService.resolve(token);

            if (userId == null) {
                throw new BusinessException(ErrorCode.NOT_AUTH);
            }

            userPreference = userPreferenceService.queryUserPreference(userId);
            ownerKey = "user:" + userId;
        }

        Map<String, Object> profileData = new LinkedHashMap<>();

        if (userPreference != null) {
            profileData.put("spicyLevel", userPreference.getSpicyLevel());
            profileData.put("preferenceText", userPreference.getPreferenceText());
        }

        String profileContext = objectMapper.writeValueAsString(profileData);

        InvocationParameters parameters = InvocationParameters.from("ownerKey", ownerKey);

        if (userId != null) {
            parameters.put("userId", userId);
        }

        if (userPreference != null) {
            parameters.put("userPreference", userPreference);
        }

        String confirmedDraftId = req.getConfirmedDraftId();

        if (confirmedDraftId != null) {
            if (confirmedDraftId.isBlank()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR);
            }

            parameters.put("confirmedDraftId", confirmedDraftId);
        }

        log.info("agent chat, len={}", req.getMessage().length());

        if(! concurrencyLimiter.trySemaphore()){
            throw new BusinessException(ErrorCode.AGENT_BUSY);
        }
        try {
            String conversationId = conversationService.resolveConversationId(
                    req.getConversationId(), ownerKey);


            conversationMemoryService.beginUse(conversationId);

            UserPreferenceProposalToolResult preferenceProposal = null;
            DraftOrderToolResult draftResult = null;
            ConfirmOrderToolResult orderResult = null;

            try {
                Result<String> result = orderingAgent.chat(req.getMessage(), parameters, profileContext, conversationId);
                log.info("当前Agent工具调用次数:{}", result.toolExecutions().size());
                for (ToolExecution execution : result.toolExecutions()) {
                    if ("proposeUserPreference".equals(execution.request().name())) {
                        // 以最后一次候选工具执行为准
                        preferenceProposal = null;

                        Object toolResult = execution.resultObject();

                        if (toolResult instanceof UserPreferenceProposalToolResult proposalResult) {
                            preferenceProposal = proposalResult;
                        }
                    }
                    if ("draftOrder".equals(execution.request().name())) {
                        draftResult = execution.resultObject()
                                instanceof DraftOrderToolResult draft ? draft : null;
                    }
                    if ("confirmOrder".equals(execution.request().name())) {
                        orderResult = execution.resultObject()
                                instanceof ConfirmOrderToolResult order ? order : null;
                    }

                    log.info("Agent调用工具:{}", execution.request().name());
                    log.debug("Agent工具调用详情: tool={}, arguments={}, result={}",
                            execution.request().name(),
                            execution.request().arguments(),
                            execution.result());
                }

                return ApiResponse.success(
                        new AgentChatResponse(conversationId,
                                result.content(),
                                preferenceProposal, draftResult, orderResult
                        ));

            } catch (Exception e) {
                log.warn("当前调用agent出现问题", e);
            } finally {
                conversationMemoryService.endUse(conversationId);
            }
        }

        finally {
            concurrencyLimiter.release();
        }

        return ApiResponse.error(ErrorCode.LLM_SERVICE_UNAVAILABLE);
    }

    /**
     *  这个接口是流式输出的接口，这里的SseEmitter是 MVC的一个类，也是负责流式输出的
     *  而 langchain4j 本身就有TokenStream，来接受模型的逐段流式输出，这两个是相辅相成的
     */
    @PostMapping(value = "/chat/stream",
                produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody AgentChatRequest req,
                                 HttpServletResponse response){
        String token = httpServletRequest.getHeader("X-User-Token");
        String ownerKey;
        Long userId = null;
        UserPreferenceDTO userPreference = null;

        //游客:取得现有对话的身份
        //给游客一个临时的ownerKey
        if(token == null){
            ownerKey = "guest" + httpServletRequest.getSession().getId();
        }

        //注册用户：先验证 token，再得到真实用户编号
        //然后得到真实用户编号userId，然后取出注册用户的画像
        else {
            userId = tokenService.resolve(token);

            if (userId == null) {
                throw new BusinessException(ErrorCode.NOT_AUTH);
            }

            userPreference = userPreferenceService.queryUserPreference(userId);
            ownerKey = "user:" + userId;
        }

        //创建一个profileData来存用户的口味偏好
        Map<String, Object> profileData = new LinkedHashMap<>();

        if (userPreference != null) {
            profileData.put("spicyLevel", userPreference.getSpicyLevel());
            profileData.put("preferenceText", userPreference.getPreferenceText());
        }

        //把上述的profileData转成文本的给LLM看
        String profileContext = objectMapper.writeValueAsString(profileData);

        /**
         *  InvocationParameters 是 LangChain4j 提供的“本次调用上下文”容器。
         *  你可以先把它理解成一个类似 Map<String, Object> 的对象，用来把后端掌握的信息传给工具。
         */
        InvocationParameters parameters = InvocationParameters.from("ownerKey", ownerKey);

        if (userId != null) {
            parameters.put("userId", userId);
        }

        if (userPreference != null) {
            parameters.put("userPreference", userPreference);
        }

        //处理 confirmedDraftId
        String confirmedDraftId = req.getConfirmedDraftId();

        if (confirmedDraftId != null) {
            if (confirmedDraftId.isBlank()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR);
            }

            parameters.put("confirmedDraftId", confirmedDraftId);
        }

        //创建一个SseEmitter，发送流式消息
        SseEmitter emitter = new SseEmitter(60_000L);
        //取得全局并发许可
        AtomicBoolean resourcesReleased = new AtomicBoolean(false);

        if (!concurrencyLimiter.trySemaphore()) {
            throw new BusinessException(ErrorCode.AGENT_BUSY);
        }

        final String conversationId;

        //假设beginUse()因为"同会话正在处理"而失败，本次请求只归还刚取得的全局许可
        try {
            conversationId = conversationService.resolveConversationId(
                    req.getConversationId(), ownerKey);

            conversationMemoryService.beginUse(conversationId);

        } catch (RuntimeException | Error e) {
            concurrencyLimiter.release();
            throw e;
        }

        try {
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            response.setHeader("X-Accel-Buffering", "no");

            //创建流式执行对象，把需要的东西交给它
            AgentChatStreamSession session = new AgentChatStreamSession(
                    //后续向浏览器发送事件
                    emitter,
                    //标识本次使用的对话聊天
                    conversationId,
                    //任务结束的时候，执行资源归还
                    () -> releaseStreamResources(conversationId, resourcesReleased));
            //启动session，返回emitter
            session.start(() -> orderingAgent.chatStream(
                    req.getMessage(), parameters, profileContext, conversationId));

            return emitter;
        } catch (RuntimeException | Error e) {
            // 回调注册或流式初始化失败，也必须归还已经登记的资源。
            releaseStreamResources(conversationId, resourcesReleased);
            throw e;
        }
    }

    private void releaseStreamResources(
            String conversationId,
            AtomicBoolean resourcesReleased) {

        if (!resourcesReleased.compareAndSet(false, true)) {
            return;
        }

        try {
            conversationMemoryService.endUse(conversationId);
        } finally {
            concurrencyLimiter.release();
        }
    }
}
