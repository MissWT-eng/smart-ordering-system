package com.luwei.ordering.mapper;

import com.luwei.ordering.common.enums.OrderStatus;
import com.luwei.ordering.entity.OrdersEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
@Mapper
public interface OrderMapper {
    /**
     *  插入订单主表,回填一个orderNum;
     */
    int insertOrder(OrdersEntity entity);

    /**
     *  分页查询用户的订单主表
     */
    List<OrdersEntity> findByUserId(@Param("userId") Long userId ,
                                    @Param("offset") Long offset ,
                                    @Param("limit") Integer limit);

    /**
     *  统计该用户的订单总数
     */
    long countByUserId(@Param("userId") Long userId);

    /**
     *  根据订单号查询订单
     */
    OrdersEntity findByOrderNum(@Param("orderNum") Long orderNum);

    /**
     *  更新订单状态
     */
    int updateOrderStatus(OrdersEntity ordersEntity);
}
