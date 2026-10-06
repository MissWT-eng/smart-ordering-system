package com.luwei.ordering.controller;

import com.luwei.ordering.dto.request.RecommendationRequest;
import com.luwei.ordering.dto.response.ApiResponse;
import com.luwei.ordering.dto.response.RecommendationResponse;
import com.luwei.ordering.service.RecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/recommendations")
@RequiredArgsConstructor
public class RecommendationController {
    private final RecommendationService recommendationService;

    /**
     *  AI智能推荐接口
     */
    @PostMapping
    public ApiResponse<RecommendationResponse> recommend(@Valid @RequestBody
                                                         RecommendationRequest request,
                                                         @RequestHeader(value = "X-User-Token", required = false) String token)
    {
        return ApiResponse.success(recommendationService.recommendDishes(request,token));
    }
}
