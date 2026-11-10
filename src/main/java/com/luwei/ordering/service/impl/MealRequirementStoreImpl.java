package com.luwei.ordering.service.impl;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.luwei.ordering.model.conversation.MealRequirement;
import com.luwei.ordering.service.MealRequirementStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;      

@Service 
@Slf4j 
@RequiredArgsConstructor 
public class MealRequirementStoreImpl implements MealRequirementStore {
    private static final String KEY_PREFIX = "chat:requirement:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value ("${auth.conversation-ttl-minutes}")
    private long ttlMinute;


    @Override
    public MealRequirement get(String conversationId) {
        if(conversationId == null || conversationId.isBlank()){
            return null;
        }
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + conversationId);
        if(json == null || json.isBlank()){
            return null;
        }
        try{
            return objectMapper.readValue(json, MealRequirement.class);
        }
        catch(Exception e){
            //读失败的话直接返回一个空结果就行，不影响点餐
            log.warn("读取本轮需求失败，conversationId:{}", conversationId, e);
            return null;
        }
    }

    @Override
    public void merge(String conversationId, MealRequirement partial) {
         if(ttlMinute <= 0){return;}
         if(conversationId == null || conversationId.isBlank() || partial == null){
            return; 
         }
         try{
            MealRequirement old = get(conversationId);
            MealRequirement merged = new MealRequirement(
                partial.peopleCount() != null ? partial.peopleCount() : (old != null ? old.peopleCount() : null),
                partial.dishCount() != null ? partial.dishCount() : (old != null ? old.dishCount() : null),
                partial.preference() != null && !partial.preference().isBlank() ? partial.preference() : (old != null ? old.preference() : null),
                partial.budget() != null ? partial.budget() : (old != null ? old.budget() : null)
            );

            redisTemplate.opsForValue().set(KEY_PREFIX + conversationId, objectMapper.writeValueAsString(merged), Duration.ofMinutes(ttlMinute));
         }
         catch(Exception e){
            log.warn("本轮需求保存失败，conversationId:{}", conversationId, e);
         }
    }

}