package com.luwei.ordering.service.impl;

import com.luwei.ordering.common.enums.DishStatus;
import com.luwei.ordering.common.enums.UserStatus;
import com.luwei.ordering.common.exception.BusinessException;
import com.luwei.ordering.common.exception.ErrorCode;
import com.luwei.ordering.converter.FeedbackConverter;
import com.luwei.ordering.dto.internal.DishFeedbackStat;
import com.luwei.ordering.dto.request.FeedbackAddRequest;
import com.luwei.ordering.entity.DishEntity;
import com.luwei.ordering.entity.UserDishFeedbackEntity;
import com.luwei.ordering.entity.UserEntity;
import com.luwei.ordering.mapper.DishMapper;
import com.luwei.ordering.mapper.UserDishFeedbackMapper;
import com.luwei.ordering.mapper.UserMapper;
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
    private final UserMapper userMapper;
    private final DishMapper dishMapper;
    private final FeedbackConverter feedbackConverter;

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

    @Override
    public void addFeedback(FeedbackAddRequest feedbackAddRequest) {
        // 1. 用户存在 + 未禁用
        UserEntity entity = userMapper.findById(feedbackAddRequest.getUserId());
        if(entity == null) throw new BusinessException(ErrorCode.NOT_EXIST);
        if(entity.getUserStatus() == UserStatus.DISABLED){
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }
        // 2. 菜品存在 + 上架(dishMapper.findById + 状态判断)
        DishEntity dish = dishMapper.findById(feedbackAddRequest.getDishNumber());
        if(dish == null) throw new BusinessException(ErrorCode.NOT_EXIST);
        if(dish.getDishStatus() == DishStatus.UNAVAILABLE){
            throw new BusinessException(ErrorCode.DISH_NOT_AVAILABLE);
        }
        // 3. 分数 1~5 已由注解拦, 这里不用再查
        UserDishFeedbackEntity feedbackEntity = feedbackConverter.toEntity(feedbackAddRequest);

        // 4. feedbackMapper.insert(entity) 落库
        feedbackMapper.insert(feedbackEntity);
    }
}
