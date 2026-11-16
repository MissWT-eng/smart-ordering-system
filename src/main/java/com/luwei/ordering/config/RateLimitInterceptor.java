package com.luwei.ordering.config;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component 
@Slf4j 
@RequiredArgsConstructor 
public class RateLimitInterceptor implements  HandlerInterceptor{
    private final StringRedisTemplate redisTemplate;
    @Value ("${ratelimit.chat-per-day}")
    private Long ratelimit;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler)
    {   try{
        String ip = request.getRemoteAddr();
        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String key = "ratelimit:chat:" + ip + ":" + day;

        Long count = redisTemplate.opsForValue().increment(key);
        if(count != null && count == 1L){
            redisTemplate.expire(key, Duration.ofHours(25));
        }
        
        if(count != null && count > ratelimit){
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":429,\"msg\":\"今日体验次数已用完，明天再来\",\"data\":null}");
            return false;
        }
        return true;
    }
    catch(Exception e){
        log.warn("限流技术失败，本次放行", e);
        return true;
    }

    }
}