package com.luwei.ordering.service.impl;

import com.luwei.ordering.common.exception.BusinessException;
import com.luwei.ordering.common.exception.ErrorCode;
import com.luwei.ordering.model.conversation.ConversationSession;
import com.luwei.ordering.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {
    private final StringRedisTemplate redisTemplate;
    @Value("${auth.conversation-ttl-minutes}")
    private long ttlMinute;
    private final String instanceId = UUID.randomUUID().toString();
    private final ObjectMapper objectMapper;

    public static final String luaText = """
            -- 1. 读取 Redis 中的会话记录
            local raw = redis.call('GET', KEYS[1])
            
            -- 不存在，说明会话不存在或已经过期
            if not raw then
                return 0
            end
            
            -- 2. 尝试把 JSON 字符串解析成 Lua table
            local ok, session = pcall(cjson.decode, raw)
            
            -- 兼容以前直接保存 ownerKey 的旧记录
            if not ok or type(session) ~= 'table' then
                if raw ~= ARGV[1] then
                    return -1
                end
            
                -- 属于当前用户，但没有启动编号，要求重新开启对话
                return -2
            end
            
            -- 3. 先检查会话是否属于当前用户
            if session.ownerKey ~= ARGV[1] then
                return -1
            end
            
            -- 4. 再检查是否属于本次启动
            if session.instanceId ~= ARGV[3] then
                return -2
            end
            
            -- 5. 所有检查通过，才刷新有效期
            return redis.call('PEXPIRE', KEYS[1], ARGV[2])
            """;

    @Override
    public String resolveConversationId(String conversationId, String ownerKey) {
        //验证一下这个ttlMinutes是不是合法的
        if(ttlMinute <= 0){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }

        //验证是不是第一次对话，如果是第一次对话，验证当前ownerKey并下发conversationId
        if(conversationId == null){
            //验证ownerKey是否为空
            if(ownerKey == null || ownerKey.isBlank()){throw new BusinessException(ErrorCode.PARAM_ERROR);}
            //生成conversationId
            conversationId = UUID.randomUUID().toString();
            String redisKey = "chat:conversation:" + conversationId;
            ConversationSession session = new ConversationSession(ownerKey,instanceId);
            String json = objectMapper.writeValueAsString(session);
            //存储conversationId和发起对话的ownerKey
            redisTemplate.opsForValue().set(redisKey, json, Duration.ofMinutes(ttlMinute));

            return conversationId;
        }

        else{
            //验证ownerKey是否为空
            if(ownerKey == null || ownerKey.isBlank()){throw new BusinessException(ErrorCode.PARAM_ERROR);}
            //做空白编号检查
            if(conversationId.isBlank()) throw new BusinessException(ErrorCode.PARAM_ERROR);
            //延续对话时长
            validateAndRefreshConversation(conversationId, ownerKey);
            return conversationId;
        }
    }


    //这里规定 1 正常结束方法
    //0 抛出会话不正确或已经过期
    //-1 抛出FORBIDDEN
    //null或其他值 抛出系统异常
    private void validateAndRefreshConversation(
            String conversationId, String ownerKey) {
        // 原子校验归属并续期
        String redisKey = "chat:conversation:" + conversationId;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(luaText, Long.class);
        Long result = redisTemplate.execute(
                script,
                List.of(redisKey),
                ownerKey,
                String.valueOf(Duration.ofMinutes(ttlMinute).toMillis()),
                instanceId
        );

        if (result == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
        if (result == 0L) {
            throw new BusinessException(
                    ErrorCode.CONVERSATION_NOT_FOUND_OR_EXPIRED);
        }
        if (result == -1L) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (result == -2L) {
            throw new BusinessException(
                    ErrorCode.CONVERSATION_CONTEXT_LOST);
        }
        if (result != 1L) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }

}
