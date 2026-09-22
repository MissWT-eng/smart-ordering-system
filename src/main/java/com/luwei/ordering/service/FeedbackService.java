package com.luwei.ordering.service;

import java.util.Map;

public interface FeedbackService {
    //返回反馈中的平均分 -1 ~ 1,没有在Map中的菜品为0
    Map<Long , Double> getAvgScoreByDish(Long userId);

    Map<Long , Double> getAffinityByDish(Long userId);
}
