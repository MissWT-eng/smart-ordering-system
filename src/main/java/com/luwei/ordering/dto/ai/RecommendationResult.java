package com.luwei.ordering.dto.ai;
import java.util.List;

public record RecommendationResult(
        Long primaryDishNumber,
        List<DishReason> recommendations,
        String summary)
{}
