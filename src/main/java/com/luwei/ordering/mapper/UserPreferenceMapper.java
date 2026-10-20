package com.luwei.ordering.mapper;

import com.luwei.ordering.entity.UserEntity;
import com.luwei.ordering.entity.UserPreferenceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserPreferenceMapper {
    UserPreferenceEntity findByUserId(@Param("userId") Long userId);

    //先尝试插入；如果主键或唯一键重复，就更新已有的那条记录 (ON DUPLICATE KEY UPDATE)
    void saveByUserId(UserPreferenceEntity entity);
    void deleteByUserId(@Param("userId")Long userId);
}
