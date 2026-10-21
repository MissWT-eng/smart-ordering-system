package com.luwei.ordering.service.impl;

import com.luwei.ordering.agent.OrderingAgent;
import com.luwei.ordering.common.exception.BusinessException;
import com.luwei.ordering.common.exception.ErrorCode;
import com.luwei.ordering.service.ConversationMemoryService;
import dev.langchain4j.memory.ChatMemory;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationMemoryServiceImpl implements ConversationMemoryService {
    private final OrderingAgent orderingAgent;
    private final StringRedisTemplate redisTemplate;
    private final ConcurrentHashMap<String, Integer> activeCalls =
            new ConcurrentHashMap<>();

    @Value("${auth.max-cached-conversations}")
    private int maxCachedConversations;

    private Semaphore cachedConversationSlots;

    @PostConstruct
    public void initCachedConversationSlots() {
        if (maxCachedConversations <= 0){
            throw new IllegalArgumentException("max-cached-conversations 必须大于 0");
        }
        cachedConversationSlots = new Semaphore(maxCachedConversations);
    }

    @Override
    public void clearConversationMemory(String conversationId) {
        ChatMemory chatMemory = orderingAgent.getChatMemory(conversationId);
        if(chatMemory != null) chatMemory.clear();
        orderingAgent.evictChatMemory(conversationId);
    }

    @Override
    @Scheduled(fixedDelay = 60_000)
    public void cleanupExpiredConversations() {
        for(String conversationId : activeCalls.keySet()){
            try{
                activeCalls.computeIfPresent(
                                            conversationId,
                                            (key,count) -> {if(count != 0){
                                                return count;
                                            }
               Boolean exists = redisTemplate.hasKey(
                        "chat:conversation:" + key);


               if (!Boolean.FALSE.equals(exists)) {
                         return count;
               }
               clearConversationMemory(key);
               cachedConversationSlots.release();
               return null;
               }

                );
            }
            catch (Exception e) {
                log.warn("清理会话记忆失败，稍后重试: conversationId={}",
                        conversationId, e);
            }
        }
    }


    //count表示当前正在执行的调用数
    @Override
    public void beginUse(String conversationId) {
        activeCalls.compute(conversationId,(key,olderCount) -> {
            String redisKey = "chat:conversation:" + conversationId;
            if(redisTemplate.opsForValue().get(redisKey) == null){
                throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND_OR_EXPIRED);
            }
            if (olderCount != null && olderCount > 0) {
                throw new BusinessException(ErrorCode.CONVERSATION_BUSY);
            }
            if(olderCount == null && ! cachedConversationSlots.tryAcquire()){
                throw new BusinessException(ErrorCode.CONVERSATION_CAPACITY_EXCEEDED);
            }
            return 1;
        });
    }


    @Override
    public void endUse(String conversationId) {
        activeCalls.compute(conversationId, (key, count) -> {
            if (count == null || count <= 0) {
                throw new IllegalStateException("会话使用计数不匹配");
            }
            return count - 1;
        });
    }

}
