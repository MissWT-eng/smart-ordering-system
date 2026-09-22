package com.luwei.ordering.service;

import com.luwei.ordering.dto.request.RecommendationRequest;
import com.luwei.ordering.dto.response.RecommendationResponse;

public interface RecommendationService {
    RecommendationResponse recommendDishes(RecommendationRequest request);
}
