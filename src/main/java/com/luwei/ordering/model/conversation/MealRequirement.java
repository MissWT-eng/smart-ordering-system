package com.luwei.ordering.model.conversation;

import java.math.BigDecimal;

public record MealRequirement(
    Integer peopleCount,
    Integer dishCount,
    String preference,
    BigDecimal budget
){}