package com.luwei.ordering.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemEntity {
    /**
     *   明细ID
     */
    private Long itemId;

    /**
     *  所属订单编号
     */
    private Long orderNum;

    /**
     *  菜品号
     */
    private Long dishNumber;

    /**
     *  数量
     */
    private Integer quantity;

    /**
     *  当前时间的实价
     */
    private BigDecimal priceAtTime;
}
