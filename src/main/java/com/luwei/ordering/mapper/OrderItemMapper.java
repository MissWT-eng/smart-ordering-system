package com.luwei.ordering.mapper;

import com.luwei.ordering.entity.OrderItemEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderItemMapper {
    /**
     *  批量插入订单明细
     */
    int insertBatch(List<OrderItemEntity> items);

    /**
     *  查某笔订单的明细
     */
    List<OrderItemEntity> findByOrderNum(@Param("orderNum") Long orderNum);
}
