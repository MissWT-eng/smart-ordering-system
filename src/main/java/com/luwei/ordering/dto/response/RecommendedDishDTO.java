package com.luwei.ordering.dto.response;

public record RecommendedDishDTO(DishListItemDTO dish ,
                                 String reason,
                                 boolean primary) {}

