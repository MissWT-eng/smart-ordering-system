package com.luwei.ordering.controller;

import com.luwei.ordering.dto.request.FeedbackAddRequest;
import com.luwei.ordering.dto.response.ApiResponse;
import com.luwei.ordering.service.FeedbackService;
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

    @PostMapping
    public ApiResponse<Void> addFeedback(@Valid @RequestBody FeedbackAddRequest request){
        feedbackService.addFeedback(request);
        return ApiResponse.success();
    }

}
