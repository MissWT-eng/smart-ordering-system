package com.luwei.ordering.controller;

import com.luwei.ordering.common.exception.BusinessException;
import com.luwei.ordering.common.exception.ErrorCode;
import com.luwei.ordering.dto.request.FeedbackAddRequest;
import com.luwei.ordering.dto.response.ApiResponse;
import com.luwei.ordering.service.FeedbackService;
import com.luwei.ordering.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/feedback")
@RequiredArgsConstructor
public class FeedbackController {
    private final FeedbackService feedbackService;
    private final TokenService tokenService;

    @PostMapping
    public ApiResponse<Void> addFeedback(
            @Valid @RequestBody FeedbackAddRequest request,
            HttpServletRequest httpServletRequest) {

        String token = httpServletRequest.getHeader("X-User-Token");
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.NOT_AUTH);
        }

        Long userId = tokenService.resolve(token);
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_AUTH);
        }

        // 使用凭证对应的身份，覆盖请求体可能传入的编号
        request.setUserId(userId);

        feedbackService.addFeedback(request);
        return ApiResponse.success();
    }

}
