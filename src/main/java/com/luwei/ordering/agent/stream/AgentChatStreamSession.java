package com.luwei.ordering.agent.stream;

import com.luwei.ordering.common.exception.ErrorCode;
import com.luwei.ordering.dto.response.AgentChatResponse;
import com.luwei.ordering.dto.response.ApiResponse;
import com.luwei.ordering.dto.response.ConfirmOrderToolResult;
import com.luwei.ordering.dto.response.DraftOrderToolResult;
import com.luwei.ordering.dto.response.UserPreferenceProposalToolResult;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 每次流式请求独立创建。连接关闭与模型任务结束是两个不同的状态。
 * 断连或 SSE 超时只停止输出；已启动的任务结束后才归还会话计数和许可。
 */
@Slf4j
public final class AgentChatStreamSession {
    private final SseEmitter emitter;
    private final String conversationId;
    private final Runnable releaseResources;
    private final Object stateLock = new Object();

    // 以下状态均在 stateLock 内访问，避免超时与模型回调交叉发送事件。
    private boolean started;
    private boolean connectionClosed;
    private boolean taskFinished;
    private UserPreferenceProposalToolResult proposalToolResult;
    private DraftOrderToolResult draftToolResult;
    private ConfirmOrderToolResult orderToolResult;

    public AgentChatStreamSession(SseEmitter emitter, String conversationId,
                                  Runnable releaseResources) {
        this.emitter = Objects.requireNonNull(emitter);
        this.conversationId = Objects.requireNonNull(conversationId);
        this.releaseResources = Objects.requireNonNull(releaseResources);

        emitter.onTimeout(this::onConnectionTimeout);
        emitter.onError(error -> onConnectionClosed());
        emitter.onCompletion(this::onConnectionClosed);
    }

    public void start(Supplier<TokenStream> streamSupplier) {
        boolean ready;
        synchronized (stateLock) {
            if (started || taskFinished) {
                return;
            }
            started = true;
            ready = !connectionClosed
                    && sendEventLocked("meta", Map.of("conversationId", conversationId));
        }

        if (!ready) {
            // 模型尚未启动，此时可直接结束任务并释放资源。
            finish("error", generationError());
            return;
        }

        try {
            TokenStream stream = Objects.requireNonNull(streamSupplier.get(), "TokenStream不能为空");
            stream.onPartialResponse(this::onPartialResponse)
                    .onToolExecuted(this::onToolExecuted)
                    .onCompleteResponse(this::onCompleteResponse)
                    .onError(this::onModelError)
                    .start();
        } catch (RuntimeException e) {
            onModelError(e);
        } catch (Error e) {
            onModelError(e);
            throw e;
        }
    }

    private void onPartialResponse(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        synchronized (stateLock) {
            if (!taskFinished && !connectionClosed) {
                sendEventLocked("delta", Map.of("text", text));
            }
        }
    }

    private void onToolExecuted(ToolExecution execution) {
        if (execution == null || execution.request() == null) {
            log.warn("流式工具执行记录缺失: conversationId={}", conversationId);
            return;
        }
        synchronized (stateLock) {
            if (taskFinished) {
                return;
            }
            log.info("流式Agent调用工具:{}", execution.request().name());
            log.debug("流式Agent工具调用详情: tool={}, arguments={}, result={}",
                    execution.request().name(), execution.request().arguments(), execution.result());

            if ("proposeUserPreference".equals(execution.request().name())) {
                // 最后一次失败也必须覆盖之前的成功候选。
                proposalToolResult = execution.resultObject()
                        instanceof UserPreferenceProposalToolResult result ? result : null;
            }
            if ("draftOrder".equals(execution.request().name())) {
                draftToolResult = execution.resultObject()
                        instanceof DraftOrderToolResult result ? result : null;
            }
            if ("confirmOrder".equals(execution.request().name())) {
                orderToolResult = execution.resultObject()
                        instanceof ConfirmOrderToolResult result ? result : null;
            }
        }
    }

    private void onCompleteResponse(ChatResponse response) {
        if (response == null || response.aiMessage() == null || response.aiMessage().text() == null) {
            onModelError(new IllegalStateException("模型未返回完整回复"));
            return;
        }
        AgentChatResponse data;
        synchronized (stateLock) {
            data = new AgentChatResponse(conversationId, response.aiMessage().text(),
                    proposalToolResult, draftToolResult, orderToolResult);
        }
        finish("done", data);
    }

    private void onModelError(Throwable error) {
        synchronized (stateLock) {
            if (taskFinished) {
                return;
            }
        }
        log.warn("流式Agent调用失败: conversationId={}", conversationId, error);
        finish("error", generationError());
    }

    private ApiResponse<Void> generationError() {
        return ApiResponse.error(ErrorCode.LLM_SERVICE_UNAVAILABLE, "本次回复生成失败");
    }

    private void finish(String eventName, Object data) {
        boolean shouldRelease = false;
        try {
            synchronized (stateLock) {
                if (taskFinished) {
                    return;
                }
                taskFinished = true;
                shouldRelease = true;
                if (!connectionClosed && sendEventLocked(eventName, data)) {
                    completeConnectionLocked();
                }
            }
        } finally {
            if (shouldRelease) {
                try {
                    releaseResources.run();
                } catch (RuntimeException e) {
                    log.error("流式会话资源释放失败: conversationId={}", conversationId, e);
                }
            }
        }
    }

    private void onConnectionTimeout() {
        synchronized (stateLock) {
            if (connectionClosed) {
                return;
            }
            // Spring 已将 emitter 标为 TIMEOUT，此时不能再 send 错误帧。
            // 客户端按“连接结束但没有 done”判断回复未完成。
            completeConnectionLocked();
        }
        // 不在这里释放资源：模型或工具可能仍在执行。
    }

    private void onConnectionClosed() {
        synchronized (stateLock) {
            connectionClosed = true;
        }
    }

    /** 调用方持有 stateLock；写入失败后不再发送，模型仍等待真实终止回调。 */
    private boolean sendEventLocked(String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data, MediaType.APPLICATION_JSON));
            return true;
        } catch (IOException e) {
            connectionClosed = true;
            // Servlet 容器负责处理这次断连，不再调用 completeWithError。
            log.debug("SSE连接写入失败: conversationId={}", conversationId, e);
        } catch (RuntimeException e) {
            connectionClosed = true;
            log.warn("SSE事件发送失败: conversationId={}, event={}", conversationId, eventName, e);
            emitter.completeWithError(e);
        }
        return false;
    }

    private void completeConnectionLocked() {
        connectionClosed = true;
        emitter.complete();
    }
}
