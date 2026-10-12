package com.luwei.ordering.service;

import com.luwei.ordering.dto.request.DraftOrderRequest;
import com.luwei.ordering.dto.request.RecommendationRequest;
import com.luwei.ordering.dto.response.DraftOrderResponse;
import com.luwei.ordering.dto.response.RecommendationResponse;

public interface RecommendationService {
    RecommendationResponse recommendDishes(RecommendationRequest request,String token);
    DraftOrderResponse draftOrder(DraftOrderRequest req);
}
