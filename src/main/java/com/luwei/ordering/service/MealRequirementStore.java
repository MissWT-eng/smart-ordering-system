package com.luwei.ordering.service;

import com.luwei.ordering.model.conversation.MealRequirement;

public interface MealRequirementStore {
    MealRequirement get(String conversationId);
    void merge(String conversationId, MealRequirement partial);
}
