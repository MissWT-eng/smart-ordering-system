package com.luwei.ordering.mapper;

import com.luwei.ordering.dto.response.DishHit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DishRecallMapper {

    /**
     * 热销：指定时间之后被点次数最多的菜品
     */
    List<DishHit> findHotDishCounts(@Param("since") LocalDateTime since,
                                    @Param("limit") int limit);

    /**
     * 复购：某位注册用户点过的菜品及次数
     */
    List<DishHit> findUserDishCounts(@Param("userId") Long userId,
                                     @Param("limit") int limit);
}
