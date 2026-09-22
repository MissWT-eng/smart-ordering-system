package com.luwei.ordering.mapper;

import com.luwei.ordering.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    /**
     *  根据唯一键 phone查找用户 做存在性检验
     */
    UserEntity findByPhone(@Param("phoneNumber") String phoneNumber);

    /**
     *   未注册用户创建对应用户
     */
    Long insertUser(UserEntity entity);

    /**
     *  根据用户 id查找用户
     */
    UserEntity findById(@Param("userId") Long userId);
}
