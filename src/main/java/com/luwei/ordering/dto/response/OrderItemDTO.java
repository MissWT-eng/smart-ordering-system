package com.luwei.ordering.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemDTO {
    /**
     *  菜品编号
     */
    private Long dishNumber;

    /**
     *  菜品名称(冗余,方便前端直接展示)
     */
    private String dishName;

    /**
     *  购买数量
     */
    private Integer quantity;

    /**
     *  下单时的价格快照
     */
    private BigDecimal priceAtTime;
}
