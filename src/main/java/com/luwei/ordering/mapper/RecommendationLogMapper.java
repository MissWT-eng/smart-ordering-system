package com.luwei.ordering.mapper;

import com.luwei.ordering.entity.RecommendationLogEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RecommendationLogMapper {
    /**
     *  推荐记录落库
     */
     int insertLog(RecommendationLogEntity entity);
}
