package com.luwei.ordering.service.impl;

import com.luwei.ordering.common.exception.BusinessException;
import com.luwei.ordering.common.exception.ErrorCode;
import com.luwei.ordering.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {
    private final StringRedisTemplate redisTemplate;

    @Value("${auth.conversation-ttl-minutes}")
    private long ttlMinute;

    @Override
    public String resolveConversationId(String conversationId, String ownerKey) {
        //验证是不是第一次对话，如果是第一次对话，验证当前ownerKey并下发conversationId
        if(conversationId == null){
            //验证ownerKey是否为空
            if(ownerKey == null || ownerKey.isBlank()){throw new BusinessException(ErrorCode.PARAM_ERROR);}
            //生成conversationId
            conversationId = UUID.randomUUID().toString();
            String redisKey = "chat:conversation:" + conversationId;
            //存储conversationId和发起对话的ownerKey TODO 后续可以重新设计对话延续时长
            redisTemplate.opsForValue().set(redisKey, ownerKey, Duration.ofMinutes(ttlMinute));

            return conversationId;
        }

        else{
            //验证ownerKey是否为空
            if(ownerKey == null || ownerKey.isBlank()){throw new BusinessException(ErrorCode.PARAM_ERROR);}
            //做空白编号检查
            if(conversationId.isBlank()) throw new BusinessException(ErrorCode.PARAM_ERROR);
            //组装查询的Redis的Key
            String checkRedisKey = "chat:conversation:" + conversationId;
            //查询获取当前对话ownerKey
            String checkOwnerKey = redisTemplate.opsForValue().get(checkRedisKey);
            //先验证是不是为空
            if(checkOwnerKey== null || checkOwnerKey.isBlank()) throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND_OR_EXPIRED);
            //不为空再检验是否是当前请求用户ownerKey
            if(! ownerKey.equals(checkOwnerKey)) throw new BusinessException(ErrorCode.FORBIDDEN);

            return conversationId;
        }
    }

}
