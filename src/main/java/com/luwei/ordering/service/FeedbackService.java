package com.luwei.ordering.service;

import com.luwei.ordering.dto.request.FeedbackAddRequest;

import java.util.Map;

public interface FeedbackService {
    //返回原始均分1 ~ 5,没有在Map中的菜品为0
    Map<Long , Double> getAvgScoreByDish(Long userId);

    Map<Long , Double> getAffinityByDish(Long userId);

    void addFeedback(FeedbackAddRequest feedbackAddRequest);
}
