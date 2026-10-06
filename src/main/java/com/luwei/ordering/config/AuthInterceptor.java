package com.luwei.ordering.config;

import com.luwei.ordering.common.exception.BusinessException;
import com.luwei.ordering.common.exception.ErrorCode;
import com.luwei.ordering.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {
    private final TokenService tokenService;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler)
    {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;

        @SuppressWarnings("unchecked")
        Map<String, String> pathVars = (Map<String, String>) request
                .getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);

        String token = request.getHeader("X-User-Token");
        if(token == null) throw new BusinessException(ErrorCode.NOT_AUTH);

        Long tokenUserId = tokenService.resolve(token);
        if (tokenUserId == null) throw new BusinessException(ErrorCode.NOT_AUTH);
        String pathUserId = (pathVars == null) ? null : pathVars.get("userId");

        if (pathUserId != null && !pathUserId.equals(String.valueOf(tokenUserId))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        return true;
    }
}
