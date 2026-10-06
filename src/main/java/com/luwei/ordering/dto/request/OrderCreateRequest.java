package com.luwei.ordering.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class OrderCreateRequest {
    //第一版实现文档是要求游客可以不注册订单
    private Long userId;

    @NotNull(message = "订单明细不能为空")
    @Size(min = 1, max = 20, message = "一单至少1道菜,最多20道")
    private List<OrderItemRequest> orderItems;
}
