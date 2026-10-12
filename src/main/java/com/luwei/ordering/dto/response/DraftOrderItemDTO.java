package com.luwei.ordering.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DraftOrderItemDTO {
    /**
     *  菜品编号
     */
    private Long dishNumber;

    /**
     *  菜品名称
     */
    private String dishName;

    /**
     *  单价
     */
    private BigDecimal unitPrice;

    /**
     *  菜品数量
     */
    private Integer quantity;

    /**
     *  小计
     */
    private BigDecimal subTotal;
}
