package com.luwei.ordering.dto.request;

import com.luwei.ordering.common.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrderUpdateRequest {

    @NotNull(message = "订单状态不能为空")
    private OrderStatus orderStatus;

    @NotNull(message = "订单号不能为空")
    private Long orderNum;

}
