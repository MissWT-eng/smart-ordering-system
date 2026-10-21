package com.luwei.ordering;

import com.luwei.ordering.agent.stream.AgentChatStreamSession;
import com.luwei.ordering.common.enums.SpicyLevel;
import com.luwei.ordering.dto.response.AgentChatResponse;
import com.luwei.ordering.dto.response.ApiResponse;
import com.luwei.ordering.dto.response.DraftOrderToolResult;
import com.luwei.ordering.dto.response.ConfirmOrderToolResult;
import com.luwei.ordering.dto.response.UserPreferenceProposalDTO;
import com.luwei.ordering.dto.response.UserPreferenceProposalToolResult;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutionResult;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class AgentChatStreamSessionTest {
    @Test
    void exposesActualDraftAndOrderObjectsWithoutParsingModelText() {
        Fixture f = new Fixture();
        f.start();
        DraftOrderToolResult draft = new DraftOrderToolResult("actual-draft", null,
                DraftOrderToolResult.Status.SUCCESS, null, "真实报价", "等待确认");
        ConfirmOrderToolResult order = new ConfirmOrderToolResult(
                ConfirmOrderToolResult.Status.CONFIRMATION_REQUIRED, null, "尚未确认", "等待顾客");
        f.stream.tool.accept(tool("draftOrder", draft));
        f.stream.tool.accept(tool("confirmOrder", order));
        f.stream.complete.accept(response("模型文字不是订单事实"));
        AgentChatResponse done = (AgentChatResponse) f.lastEvent().data();
        assertSame(draft, done.getDraftToolResult());
        assertSame(order, done.getOrderToolResult());
    }

    @Test
    void lastFailedDraftAndMissingOrderObjectReplacePreviousResults() {
        Fixture f = new Fixture();
        f.start();
        f.stream.tool.accept(tool("draftOrder", new DraftOrderToolResult("old", null,
                DraftOrderToolResult.Status.SUCCESS, null, "旧报价", "等待")));
        DraftOrderToolResult failed = new DraftOrderToolResult(null, null,
                DraftOrderToolResult.Status.INVALID_ARGUMENTS, null, "参数错误", "调整");
        f.stream.tool.accept(tool("draftOrder", failed));
        f.stream.tool.accept(tool("confirmOrder", new ConfirmOrderToolResult(
                ConfirmOrderToolResult.Status.SUCCESS, null, "先前结果", "等待")));
        f.stream.tool.accept(tool("confirmOrder", null));
        f.stream.complete.accept(response("调整方案"));
        AgentChatResponse done = (AgentChatResponse) f.lastEvent().data();
        assertSame(failed, done.getDraftToolResult());
        assertNull(done.getOrderToolResult());
    }

    @Test
    void sendsMetaAndIncrementalTextBeforeFinalResponse() {
        Fixture f = new Fixture();
        f.start();
        assertEquals(List.of("meta"), f.eventNames());
        assertEquals(Map.of("conversationId", "conversation-test"), f.emitter.events.get(0).data());
        assertEquals(0, f.releases.get());

        f.stream.partial.accept("你好\n");
        assertEquals(List.of("meta", "delta"), f.eventNames());
        assertEquals(Map.of("text", "你好\n"), f.emitter.events.get(1).data());
        assertEquals(0, f.releases.get());
        f.stream.partial.accept("世界");
        f.stream.complete.accept(response("你好\n世界"));

        assertEquals(List.of("meta", "delta", "delta", "done"), f.eventNames());
        AgentChatResponse done = (AgentChatResponse) f.lastEvent().data();
        assertEquals("你好\n世界", done.getReply());
        assertNull(done.getProposalToolResult());
        assertEquals(1, f.emitter.completions);
        assertEquals(1, f.releases.get());
    }

    @Test
    void returnsActualToolObjectAndIgnoresOtherTools() {
        Fixture f = new Fixture();
        f.start();
        UserPreferenceProposalToolResult proposal = proposal();
        f.stream.tool.accept(tool("proposeUserPreference", proposal));
        f.stream.tool.accept(tool("queryMenu", "menu"));
        f.stream.complete.accept(response("候选尚未保存"));
        assertSame(proposal, ((AgentChatResponse) f.lastEvent().data()).getProposalToolResult());
    }

    @Test
    void lastBusinessFailureReplacesEarlierSuccessfulCandidate() {
        Fixture f = new Fixture();
        f.start();
        f.stream.tool.accept(tool("proposeUserPreference", proposal()));
        UserPreferenceProposalToolResult failed = new UserPreferenceProposalToolResult(
                UserPreferenceProposalToolResult.Status.INVALID_ARGUMENTS, null, "说明过长", "请调整");
        f.stream.tool.accept(tool("proposeUserPreference", failed));
        f.stream.complete.accept(response("请调整说明"));
        assertSame(failed, ((AgentChatResponse) f.lastEvent().data()).getProposalToolResult());
    }

    @Test
    void failedToolWithoutObjectClearsPreviousCandidate() {
        Fixture f = new Fixture();
        f.start();
        f.stream.tool.accept(tool("proposeUserPreference", proposal()));
        f.stream.tool.accept(tool("proposeUserPreference", null));
        f.stream.complete.accept(response("工具未完成"));
        assertNull(((AgentChatResponse) f.lastEvent().data()).getProposalToolResult());
    }

    @Test
    void supplierFailureSendsSafeErrorAndReleases() {
        Fixture f = new Fixture();
        f.session.start(() -> { throw new IllegalStateException("private model detail"); });
        assertEquals(List.of("meta", "error"), f.eventNames());
        ApiResponse<?> error = (ApiResponse<?>) f.lastEvent().data();
        assertEquals(504, error.getCode());
        assertFalse(error.getMsg().contains("private"));
        assertEquals(1, f.releases.get());
    }

    @Test
    void synchronousStartFailureReleases() {
        Fixture f = new Fixture();
        f.stream.onStart = () -> { throw new IllegalStateException("controlled start failure"); };
        f.start();
        assertEquals(List.of("meta", "error"), f.eventNames());
        assertEquals(1, f.releases.get());
    }

    @Test
    void modelErrorIsTerminalAndLateCallbacksAreIgnored() {
        Fixture f = new Fixture();
        f.start();
        f.stream.partial.accept("半段回复");
        f.stream.error.accept(new IllegalStateException("controlled failure"));
        f.stream.complete.accept(response("late success"));
        f.stream.partial.accept("late delta");
        f.stream.error.accept(new IllegalStateException("duplicate failure"));
        assertEquals(List.of("meta", "delta", "error"), f.eventNames());
        assertEquals(1, f.releases.get());
        assertEquals(1, f.emitter.completions);
    }

    @Test
    void timeoutClosesOutputButKeepsResourcesUntilModelEnds() {
        Fixture f = new Fixture();
        f.start();
        f.emitter.timeout.run();
        assertEquals(List.of("meta"), f.eventNames());
        assertEquals(0, f.releases.get());
        assertEquals(1, f.emitter.completions);
        f.stream.partial.accept("after timeout");
        f.stream.complete.accept(response("task actually finished"));
        assertEquals(List.of("meta"), f.eventNames());
        assertEquals(1, f.releases.get());
    }

    @Test
    void containerCompletionDoesNotReleaseRunningTask() {
        Fixture f = new Fixture();
        f.start();
        f.emitter.completion.run();
        assertEquals(0, f.releases.get());
        f.stream.partial.accept("not delivered");
        f.stream.error.accept(new IllegalStateException("model ended after disconnect"));
        assertEquals(List.of("meta"), f.eventNames());
        assertEquals(1, f.releases.get());
    }

    @Test
    void containerErrorDoesNotReleaseRunningTask() {
        Fixture f = new Fixture();
        f.start();
        f.emitter.error.accept(new IOException("client disconnected"));
        assertEquals(0, f.releases.get());
        f.stream.complete.accept(response("finished"));
        assertEquals(List.of("meta"), f.eventNames());
        assertEquals(1, f.releases.get());
    }

    @Test
    void writeFailureStopsOutputWithoutPrematureCleanup() {
        Fixture f = new Fixture();
        f.start();
        f.emitter.failNextWrite = true;
        f.stream.partial.accept("write fails");
        assertEquals(0, f.releases.get());
        assertEquals(0, f.emitter.completions);
        f.stream.partial.accept("ignored");
        f.stream.complete.accept(response("model ends"));
        assertEquals(List.of("meta"), f.eventNames());
        assertEquals(1, f.releases.get());
    }

    @Test
    void failedInitialWriteDoesNotStartModel() {
        Fixture f = new Fixture();
        f.emitter.failNextWrite = true;
        AtomicInteger supplierCalls = new AtomicInteger();
        f.session.start(() -> { supplierCalls.incrementAndGet(); return f.stream; });
        assertEquals(0, supplierCalls.get());
        assertEquals(0, f.stream.starts);
        assertEquals(1, f.releases.get());
    }

    @Test
    void racingTerminalCallbacksReleaseAndSendTerminalEventOnce() throws Exception {
        Fixture f = new Fixture();
        f.start();
        CountDownLatch ready = new CountDownLatch(8), go = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(8);
        var futures = new ArrayList<java.util.concurrent.Future<?>>();
        try {
            for (int i = 0; i < 8; i++) {
                boolean success = i % 2 == 0;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(go.await(10, TimeUnit.SECONDS));
                    if (success) f.stream.complete.accept(response("ok"));
                    else f.stream.error.accept(new IllegalStateException("controlled race"));
                    return null;
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            for (var future : futures) future.get(10, TimeUnit.SECONDS);
        } finally {
            go.countDown();
            executor.shutdownNow();
        }
        assertEquals(1, f.releases.get());
        assertEquals(2, f.emitter.events.size());
        assertTrue(List.of("done", "error").contains(f.lastEvent().name()));
        assertEquals(1, f.emitter.completions);
    }

    @Test
    void missingFinalMessageProducesErrorRatherThanSuccess() {
        Fixture f = new Fixture();
        f.start();
        f.stream.complete.accept(null);
        assertEquals(List.of("meta", "error"), f.eventNames());
        assertEquals(1, f.releases.get());
    }

    static ChatResponse response(String text) {
        return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
    }

    static UserPreferenceProposalToolResult proposal() {
        return new UserPreferenceProposalToolResult(UserPreferenceProposalToolResult.Status.SUCCESS,
                new UserPreferenceProposalDTO(SpicyLevel.MILD, "少油，喜欢菌菇"), "待确认", "确认后保存");
    }

    static ToolExecution tool(String name, Object result) {
        return ToolExecution.builder()
                .request(ToolExecutionRequest.builder().name(name).arguments("{}").build())
                .result(ToolExecutionResult.builder().result(result).resultText("tool result").build())
                .invocationContext(InvocationContext.builder().invocationId(UUID.randomUUID())
                        .interfaceName("OrderingAgent").methodName("chatStream")
                        .methodArguments(List.of()).chatMemoryId("conversation-test")
                        .invocationParameters(InvocationParameters.from("ownerKey", "guest-test"))
                        .timestampNow().build())
                .build();
    }

    private static class Fixture {
        final RecordingEmitter emitter = new RecordingEmitter();
        final ControlledTokenStream stream = new ControlledTokenStream();
        final AtomicInteger releases = new AtomicInteger();
        final AgentChatStreamSession session = new AgentChatStreamSession(
                emitter, "conversation-test", releases::incrementAndGet);
        void start() { session.start(() -> stream); }
        List<String> eventNames() { return emitter.events.stream().map(Event::name).toList(); }
        Event lastEvent() { return emitter.events.get(emitter.events.size() - 1); }
    }

    record Event(String name, Object data) {}

    private static class RecordingEmitter extends SseEmitter {
        final List<Event> events = new ArrayList<>();
        Runnable timeout, completion;
        Consumer<Throwable> error;
        boolean failNextWrite;
        int completions;
        @Override public void onTimeout(Runnable callback) { timeout = callback; }
        @Override public void onCompletion(Runnable callback) { completion = callback; }
        @Override public void onError(Consumer<Throwable> callback) { error = callback; }
        @Override public void complete() { completions++; }
        @Override public void send(SseEventBuilder builder) throws IOException {
            if (failNextWrite) { failNextWrite = false; throw new IOException("controlled write failure"); }
            String name = null;
            Object payload = null;
            for (var part : builder.build()) {
                if (part.getData() instanceof String text) {
                    if (text.startsWith("event:")) name = text.substring(6).split("\n")[0];
                } else payload = part.getData();
            }
            events.add(new Event(name, payload));
        }
    }
}

/** 可手动推进回调，验证 HTTP 已返回但模型尚未结束时的状态。 */
class ControlledTokenStream implements TokenStream {
    Consumer<String> partial;
    Consumer<ToolExecution> tool;
    Consumer<ChatResponse> complete;
    Consumer<Throwable> error;
    Runnable onStart = () -> {};
    int starts;
    @Override public TokenStream onPartialResponse(Consumer<String> handler) { partial = handler; return this; }
    @Override public TokenStream onToolExecuted(Consumer<ToolExecution> handler) { tool = handler; return this; }
    @Override public TokenStream onCompleteResponse(Consumer<ChatResponse> handler) { complete = handler; return this; }
    @Override public TokenStream onError(Consumer<Throwable> handler) { error = handler; return this; }
    @Override public TokenStream onRetrieved(Consumer<List<Content>> handler) { return this; }
    @Override public TokenStream ignoreErrors() { return this; }
    @Override public void start() { starts++; onStart.run(); }
}
