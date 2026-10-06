package com.luwei.ordering.service.impl;

import com.luwei.ordering.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {
    private final StringRedisTemplate redisTemplate;

    //过期时间
    @Value("${auth.token-ttl-hours}")
    private long ttlHours;


    @Override
    public String createToken(Long userId) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set("auth:token:" + token,userId.toString(), Duration.ofHours(ttlHours));
        return token;
    }

    @Override
    public Long resolve(String token) {
        String value = redisTemplate.opsForValue().get("auth:token:" + token);
        if (value == null) return null;
        return Long.valueOf(value);
    }

}
