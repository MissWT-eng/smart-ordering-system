package com.luwei.ordering.mapper;

import com.luwei.ordering.dto.internal.DishFeedbackStat;
import com.luwei.ordering.entity.UserDishFeedbackEntity;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface UserDishFeedbackMapper {
    long countAll();
    int insert(UserDishFeedbackEntity entity);
    List<DishFeedbackStat> selectAffinityByUser(Long userId);
}
