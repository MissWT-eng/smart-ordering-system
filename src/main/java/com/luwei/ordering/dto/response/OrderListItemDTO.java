package com.luwei.ordering.dto.response;

import com.luwei.ordering.common.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor

public class OrderListItemDTO {
    /**
     *  订单编号
     */
    private Long orderNum;

    /**
     *  下单时间
     */
    private LocalDateTime orderTime;

    /**
     *  订单状态
     */
    private OrderStatus orderStatus;

    /**
     *   订单总金额
     */
    private BigDecimal orderTotalMoney;

    /**
     *   订单明细
     */
    private List<OrderItemDTO> items;
}
