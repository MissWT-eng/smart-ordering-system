package com.luwei.ordering;

import com.luwei.ordering.agent.OrderingAgent;
import com.luwei.ordering.common.enums.SpicyLevel;
import com.luwei.ordering.common.exception.BusinessException;
import com.luwei.ordering.common.exception.ErrorCode;
import com.luwei.ordering.common.exception.GlobalExceptionHandler;
import com.luwei.ordering.concurrent.AgentChatConcurrencyLimiter;
import com.luwei.ordering.controller.AgentChatController;
import com.luwei.ordering.dto.response.UserPreferenceDTO;
import com.luwei.ordering.service.ConversationService;
import com.luwei.ordering.service.TokenService;
import com.luwei.ordering.service.UserPreferenceService;
import com.luwei.ordering.service.impl.ConversationMemoryServiceImpl;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.AsyncEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.luwei.ordering.AgentChatStreamSessionTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AgentChatStreamingControllerTest {
    private final ObjectMapper json = new ObjectMapper();
    private OrderingAgent agent;
    private HttpServletRequest request;
    private TokenService tokens;
    private UserPreferenceService profiles;
    private ConversationService conversations;
    private ConversationMemoryServiceImpl memory;
    private AgentChatConcurrencyLimiter limiter;
    private MockMvc mvc;
    private final Map<String, ControlledTokenStream> streams = new ConcurrentHashMap<>();

    @Test
    void syncAndStreamingResponsesExposeActualQuoteAndConfirmationResults() throws Exception {
        var draft = new com.luwei.ordering.dto.response.DraftOrderToolResult("draft-from-tool", null,
                com.luwei.ordering.dto.response.DraftOrderToolResult.Status.BUDGET_EXCEEDED,
                null, "超预算", "请调整");
        var order = new com.luwei.ordering.dto.response.ConfirmOrderToolResult(
                com.luwei.ordering.dto.response.ConfirmOrderToolResult.Status.CONFIRMATION_REQUIRED,
                null, "尚未确认", "等待顾客");
        when(agent.chat(anyString(), any(), anyString(), anyString())).thenReturn(
                Result.<String>builder().content("仅说明结果")
                        .toolExecutions(List.of(tool("draftOrder", draft), tool("confirmOrder", order))).build());
        mvc.perform(post("/agent/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"试算\",\"conversationId\":\"quote-sync\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.draftToolResult.draftId").value("draft-from-tool"))
                .andExpect(jsonPath("$.data.draftToolResult.status").value("BUDGET_EXCEEDED"))
                .andExpect(jsonPath("$.data.orderToolResult.status").value("CONFIRMATION_REQUIRED"));

        MvcResult stream = open("quote-stream", "试算");
        streams.get("quote-stream").tool.accept(tool("draftOrder", draft));
        streams.get("quote-stream").tool.accept(tool("confirmOrder", order));
        streams.get("quote-stream").complete.accept(response("仅说明结果"));
        mvc.perform(asyncDispatch(stream)).andExpect(status().isOk());
        JsonNode done = frames(stream).get(1).data();
        assertEquals("draft-from-tool", done.path("draftToolResult").path("draftId").asText());
        assertEquals("CONFIRMATION_REQUIRED", done.path("orderToolResult").path("status").asText());
        assertFalse(done.toString().contains("ownerKey"));
        capacity(4);
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        agent = mock(OrderingAgent.class);
        request = mock(HttpServletRequest.class);
        when(request.getSession()).thenReturn(new MockHttpSession());
        tokens = mock(TokenService.class);
        profiles = mock(UserPreferenceService.class);
        conversations = mock(ConversationService.class);
        when(conversations.resolveConversationId(nullable(String.class), anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            return id == null ? UUID.randomUUID().toString() : id;
        });
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn("existing-session");
        memory = spy(new ConversationMemoryServiceImpl(agent, redis));
        ReflectionTestUtils.setField(memory, "maxCachedConversations", 100);
        memory.initCachedConversationSlots();
        limiter = new AgentChatConcurrencyLimiter();
        when(agent.chatStream(anyString(), any(), anyString(), anyString())).thenAnswer(invocation -> {
            ControlledTokenStream stream = new ControlledTokenStream();
            streams.put(invocation.getArgument(3), stream);
            return stream;
        });
        when(agent.chat(anyString(), any(), anyString(), anyString())).thenReturn(
                Result.<String>builder().content("sync reply").toolExecutions(List.of()).build());
        AgentChatController controller = new AgentChatController(agent, request, tokens, conversations,
                memory, profiles, json, limiter);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void streamsJsonEventsBeforeCompletionAndReturnsStructuredCandidate() throws Exception {
        MvcResult result = open("stream-a", "hello");
        assertTrue(result.getRequest().isAsyncStarted());
        assertEquals("no-store", result.getResponse().getHeader("Cache-Control"));
        assertEquals("no", result.getResponse().getHeader("X-Accel-Buffering"));
        assertEquals(List.of("meta"), names(result));
        capacity(3);
        verify(memory, never()).endUse(anyString());

        ControlledTokenStream stream = streams.get("stream-a");
        stream.partial.accept("你好\n\"世界\"");
        List<Frame> partialFrames = frames(result);
        assertEquals("你好\n\"世界\"", partialFrames.get(1).data().path("text").asText());
        verify(memory, never()).endUse(anyString());
        stream.tool.accept(tool("proposeUserPreference", proposal()));
        stream.complete.accept(response("完整回复"));
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
        assertEquals(List.of("meta", "delta", "done"), names(result));
        JsonNode done = frames(result).get(2).data();
        assertEquals("stream-a", done.path("conversationId").asText());
        assertEquals("完整回复", done.path("reply").asText());
        assertEquals("SUCCESS", done.path("proposalToolResult").path("status").asText());
        assertEquals("mild", done.path("proposalToolResult").path("proposal").path("spicyLevel").asText());
        verify(memory, times(1)).endUse("stream-a");
        capacity(4);
    }

    @Test
    void sendsModelFailureAsErrorEventWithoutChangingCommittedStreamToJson() throws Exception {
        MvcResult result = open("model-error", "hello");
        streams.get("model-error").partial.accept("partial");
        streams.get("model-error").error.accept(new IllegalStateException("private diagnostic"));
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
        assertEquals(List.of("meta", "delta", "error"), names(result));
        JsonNode error = frames(result).get(2).data();
        assertEquals(504, error.path("code").asInt());
        assertFalse(error.toString().contains("private diagnostic"));
        verify(memory, times(1)).endUse("model-error");
        capacity(4);
    }

    @Test
    void passesTrustedIdentityProfileAndConfirmationIntoStreamingAgent() throws Exception {
        when(request.getHeader("X-User-Token")).thenReturn("test-user-token");
        when(tokens.resolve("test-user-token")).thenReturn(7L);
        UserPreferenceDTO profile = new UserPreferenceDTO();
        profile.setUserId(7L);
        profile.setSpicyLevel(SpicyLevel.NONE);
        profile.setPreferenceText("喜欢菌菇");
        when(profiles.queryUserPreference(7L)).thenReturn(profile);
        MvcResult result = mvc.perform(post("/agent/chat/stream").contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM).content(json.writeValueAsString(Map.of(
                                "message", "confirm", "conversationId", "trusted", "confirmedDraftId", "draft-verified"))))
                .andReturn();
        ArgumentCaptor<InvocationParameters> parameters = ArgumentCaptor.forClass(InvocationParameters.class);
        ArgumentCaptor<String> profileJson = ArgumentCaptor.forClass(String.class);
        verify(agent).chatStream(eq("confirm"), parameters.capture(), profileJson.capture(), eq("trusted"));
        assertEquals("user:7", parameters.getValue().get("ownerKey"));
        assertEquals(Long.valueOf(7), parameters.getValue().get("userId"));
        assertSame(profile, parameters.getValue().get("userPreference"));
        assertEquals("draft-verified", parameters.getValue().get("confirmedDraftId"));
        JsonNode context = json.readTree(profileJson.getValue());
        assertEquals(2, context.size());
        assertEquals("none", context.path("spicyLevel").asText());
        assertEquals("喜欢菌菇", context.path("preferenceText").asText());
        streams.get("trusted").complete.accept(response("ok"));
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk());
        capacity(4);
    }

    @Test
    void validationRejectsBlankAndOverlongMessagesAsJsonBeforeAnyAdmission() throws Exception {
        for (String message : List.of("", " \n\t ", "中".repeat(2001))) {
            mvc.perform(post("/agent/chat/stream").accept(MediaType.TEXT_EVENT_STREAM)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("message", message))))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.code").value(400));
        }
        verifyNoInteractions(conversations);
        verify(agent, never()).chatStream(anyString(), any(), anyString(), anyString());
        capacity(4);
    }

    @Test
    void rejectsInvalidTokenAndBlankConfirmationBeforeAdmission() throws Exception {
        when(request.getHeader("X-User-Token")).thenReturn("invalid-test-token");
        when(tokens.resolve("invalid-test-token")).thenReturn(null);
        mvc.perform(post("/agent/chat/stream").accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hello\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(401));
        when(request.getHeader("X-User-Token")).thenReturn(null);
        mvc.perform(post("/agent/chat/stream").accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"confirm\",\"confirmedDraftId\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(conversations);
        capacity(4);
    }

    @ParameterizedTest
    @CsvSource({"CONVERSATION_NOT_FOUND_OR_EXPIRED,404", "FORBIDDEN,403", "CONVERSATION_BUSY,409"})
    void conversationErrorsPreserveHttpStatusAndNeverLeakPermits(String name, int status) throws Exception {
        ErrorCode code = ErrorCode.valueOf(name);
        if (code == ErrorCode.CONVERSATION_BUSY) {
            doThrow(new BusinessException(code)).when(memory).beginUse("rejected");
        } else {
            when(conversations.resolveConversationId(eq("rejected"), anyString()))
                    .thenThrow(new BusinessException(code));
        }
        for (int i = 0; i < 6; i++) {
            mvc.perform(post("/agent/chat/stream").accept(MediaType.TEXT_EVENT_STREAM)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"hello\",\"conversationId\":\"rejected\"}"))
                    .andExpect(status().is(status))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.code").value(status));
            capacity(4);
        }
        verify(memory, never()).endUse(anyString());
        verify(agent, never()).chatStream(anyString(), any(), anyString(), anyString());
    }

    @Test
    void streamingAndSynchronousRequestsShareTheSameFourPermits() throws Exception {
        List<MvcResult> running = new ArrayList<>();
        try {
            for (int i = 0; i < 4; i++) running.add(open("held-" + i, "hello"));
            for (String path : List.of("/agent/chat/stream", "/agent/chat")) {
                mvc.perform(post(path).accept(MediaType.ALL).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"message\":\"overflow\",\"conversationId\":\"overflow\"}"))
                        .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value(503));
            }
            verify(conversations, never()).resolveConversationId(eq("overflow"), anyString());
            streams.get("held-0").complete.accept(response("done"));
            mvc.perform(asyncDispatch(running.get(0))).andExpect(status().isOk());
            mvc.perform(post("/agent/chat").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"hello\",\"conversationId\":\"sync-next\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
            capacity(1);
        } finally {
            for (int i = 1; i < running.size(); i++) {
                streams.get("held-" + i).complete.accept(response("done"));
                mvc.perform(asyncDispatch(running.get(i)));
            }
        }
        capacity(4);
    }

    @Test
    void sameConversationStaysBusyUntilStreamActuallyFinishes() throws Exception {
        MvcResult running = open("same-chat", "hello");
        mvc.perform(post("/agent/chat/stream").accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"second\",\"conversationId\":\"same-chat\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));
        capacity(3);
        verify(memory, never()).endUse(anyString());
        streams.get("same-chat").complete.accept(response("done"));
        mvc.perform(asyncDispatch(running));
        MvcResult next = open("same-chat", "next");
        streams.get("same-chat").complete.accept(response("next done"));
        mvc.perform(asyncDispatch(next)).andExpect(status().isOk());
        verify(memory, times(2)).endUse("same-chat");
        capacity(4);
    }

    @Test
    void agentInitializationFailureStillReturnsAnErrorEventAndReleases() throws Exception {
        when(agent.chatStream(anyString(), any(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("controlled initialization failure"));
        MvcResult result = open("start-error", "hello");
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk());
        assertEquals(List.of("meta", "error"), names(result));
        verify(memory, times(1)).endUse("start-error");
        capacity(4);
    }

    @Test
    void servletTimeoutEndsOnlyTheConnectionUntilTheModelCompletes() throws Exception {
        MvcResult running = open("timed-out", "hello");
        MockAsyncContext async = (MockAsyncContext) running.getRequest().getAsyncContext();
        for (var listener : async.getListeners()) listener.onTimeout(new AsyncEvent(async));
        mvc.perform(asyncDispatch(running)).andExpect(status().isOk());
        assertEquals(List.of("meta"), names(running));
        capacity(3);
        verify(memory, never()).endUse(anyString());

        mvc.perform(post("/agent/chat/stream").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"again\",\"conversationId\":\"timed-out\"}"))
                .andExpect(status().isConflict());
        streams.get("timed-out").complete.accept(response("actually finished"));
        assertEquals(List.of("meta"), names(running));
        verify(memory, times(1)).endUse("timed-out");
        capacity(4);
    }

    @Test
    void servletDisconnectRetainsAdmissionUntilTheModelTerminates() throws Exception {
        MvcResult running = open("disconnected", "hello");
        MockAsyncContext async = (MockAsyncContext) running.getRequest().getAsyncContext();
        for (var listener : async.getListeners()) {
            listener.onError(new AsyncEvent(async, running.getRequest(), running.getResponse(),
                    new IOException("controlled client disconnect")));
        }
        capacity(3);
        verify(memory, never()).endUse(anyString());
        streams.get("disconnected").partial.accept("not sent");
        streams.get("disconnected").complete.accept(response("actually finished"));
        assertEquals(List.of("meta"), names(running));
        verify(memory, times(1)).endUse("disconnected");
        capacity(4);
    }

    private MvcResult open(String id, String message) throws Exception {
        return mvc.perform(post("/agent/chat/stream").contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(json.writeValueAsString(Map.of("message", message, "conversationId", id))))
                .andExpect(status().isOk()).andReturn();
    }

    private List<String> names(MvcResult result) throws Exception {
        return frames(result).stream().map(Frame::name).toList();
    }

    private List<Frame> frames(MvcResult result) throws Exception {
        List<Frame> frames = new ArrayList<>();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        for (String block : body.split("\n\n")) {
            String name = null;
            StringBuilder data = new StringBuilder();
            for (String line : block.split("\n")) {
                if (line.startsWith("event:")) name = line.substring(6).trim();
                if (line.startsWith("data:")) data.append(line.substring(5));
            }
            if (name != null) frames.add(new Frame(name, json.readTree(data.toString())));
        }
        return frames;
    }

    private void capacity(int expected) {
        int acquired = 0;
        try {
            while (acquired <= expected && limiter.trySemaphore()) acquired++;
            assertEquals(expected, acquired, "可用许可数量不匹配");
        } finally {
            for (int i = 0; i < acquired; i++) limiter.release();
        }
    }

    private record Frame(String name, JsonNode data) {}
}
