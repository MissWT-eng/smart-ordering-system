package com.luwei.ordering.mapper;

import com.luwei.ordering.common.enums.DishStatus;
import com.luwei.ordering.dto.request.DishQueryRequest;
import com.luwei.ordering.entity.DishEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DishMapper {

    Long countDishes(DishQueryRequest dishQueryRequest);

    /**
     * 查询当前dish表中的菜品信息并返回
     */
    List<DishEntity> queryDishesByPage(@Param("request") DishQueryRequest request ,
                                       @Param("offset") Long offset);

    /**
     * 插入新菜品到dish表中并返回dishNumber
     */
    Long insertDish(DishEntity dishEntity);

    /**
     * 根据菜品的名称查询
     */
    int countByName(@Param("dishName") String dishName);

    /**
     * 主键查询菜品，做存在性校验
     */
    DishEntity findById(@Param("dishNumber") Long dishNumber);

    /**
     * 同名菜品检查，防止改名后存在同一菜品名
     */
    int countByNameExcludeId(@Param("dishNumber") Long dishNumber,
                             @Param("dishName") String dishName);

    /**
     * 更新菜品
     */
    int updateDish(DishEntity dishEntity);

    int updateStatus(@Param("dishStatus") DishStatus dishStatus,
                     @Param("dishNumber") Long dishNumber);
    /**
     *  获取当前所有上架的菜品
     */
    List<DishEntity> findAvailableAll();

    /**
     *  根据dishNumber获取当前上架的菜品
     */
    List<DishEntity> findByIds(@Param("dishNumbers") List<Long> ids);
}
