package com.luwei.ordering.config;

import com.luwei.ordering.common.exception.BusinessException;
import com.luwei.ordering.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;


@Component
@Slf4j
public class AdminInterceptor implements HandlerInterceptor {

    private final String adminToken;

    public AdminInterceptor(@Value("${admin.token}") String adminToken) {
        this.adminToken = adminToken;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = request.getHeader("X-Admin-Token");
        if (!StringUtils.hasText(token)
                || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                adminToken.getBytes(StandardCharsets.UTF_8))) {
            log.warn("admin token 校验失败, uri={}, ip={}",
                    request.getRequestURI(), request.getRemoteAddr());
            throw new BusinessException(ErrorCode.NOT_AUTH);
        }
        return true;
    }
}
