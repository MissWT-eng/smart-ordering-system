package com.luwei.ordering.entity;

import com.luwei.ordering.common.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor

public class OrdersEntity {
    /**
     *  订单号
     */
    private Long orderNum;

    /**
     *  订该订单的用户 id
     */
    private Long orderUserId;

    /**
     *  该笔订单的总计金额
     */
    private BigDecimal orderTotalMoney;

    /**
     *  订单时间
     */
    private LocalDateTime orderTime;

    /**
     *  订单状态(pending , paid, cancelled)
     */
    private OrderStatus orderStatus;

    /**
     *  记录订单来自哪份草稿
     */
    private String draftId;

    /**
     *  记录归属，重复确认时检查是否属于当前用户，游客也适用
     */
    private String draftOwnerKey;
}
