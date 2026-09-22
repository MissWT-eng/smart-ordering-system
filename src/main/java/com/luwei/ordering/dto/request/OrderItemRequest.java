package com.luwei.ordering.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrderItemRequest {
    @NotNull(message = "订单菜品号不能为空")
    private Long dishNumber;

    @Min(value = 1 , message = "菜品数最小为1")
    @Max(value = 50, message = "菜品数最大为50")
    @NotNull(message = "订单菜品数不能为空")
    private Integer quantity;
}
