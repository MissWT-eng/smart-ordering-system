package com.luwei.ordering.service.impl;

import com.luwei.ordering.dto.internal.DishFeedbackStat;
import com.luwei.ordering.mapper.UserDishFeedbackMapper;
import com.luwei.ordering.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor

public class FeedbackServiceImpl implements FeedbackService {
    private final UserDishFeedbackMapper feedbackMapper;

    /**
     * 注意AI最开始设计的时候没有考虑到5分制的情况下 不应该以5为中心归一化
     * 如果一个菜品用户的feedback score本生就不高 但是其他的菜品没有对应的 score 就会导致
     * 最终在计算的时候一定是做过评价的分数的菜(不论分数高低) 一定会大于没做过评价的菜品
     * 后更改为以3为中心 之后就会落到-1 ~ 1这个区间中 相应的会有奖励和惩罚
     */
    @Override
    @Transactional(readOnly = true)
    public Map<Long, Double> getAvgScoreByDish(Long userId) {
        //软反馈: 原始均分 1~5, 直接给LLM看
        return feedbackMapper.selectAffinityByUser(userId).stream().collect(Collectors.toMap(
                DishFeedbackStat::getDishNumber,
                DishFeedbackStat::getAvgScore));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Double> getAffinityByDish(Long userId) {
        //硬反馈: 居中分(-1~+1) × 置信度, 用于和向量分加权
        return feedbackMapper.selectAffinityByUser(userId).stream().collect(Collectors.toMap(
                DishFeedbackStat::getDishNumber,
                s -> {
                    double centered = (s.getAvgScore() - 3.0) / 2.0;
                    double confidence = s.getOrderCount() / (s.getOrderCount() + 2.0);
                    return centered * confidence;
                }));
    }
}
