package com.luwei.ordering.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class OrderCreateRequest {
    @NotNull(message = "该订单下单用户不能为空")
    private Long userId;

    @NotNull(message = "订单明细不能为空")
    @Size(min = 1, max = 20, message = "一单至少1道菜,最多20道")
    private List<OrderItemRequest> orderItems;
}
