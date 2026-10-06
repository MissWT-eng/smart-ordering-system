package com.luwei.ordering.mapper;

import com.luwei.ordering.entity.RecommendationLogEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RecommendationLogMapper {
    /**
     *  推荐记录落库
     */
     int insertLog(RecommendationLogEntity entity);

     /**
      * 查询用户最近一条推荐记录(回填实际点餐用)
      */
     RecommendationLogEntity findLatestByUserId(@Param("userId") Long userId);

     /**
      * 回填实际点餐菜品
      */
     int updateOrderDishes(@Param("logId") Long logId,
                           @Param("orderedDishes") String orderedDishes);
}
