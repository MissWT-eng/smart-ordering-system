package com.luwei.ordering.dto.request;

import com.luwei.ordering.common.enums.DishStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用来接收菜品上下架的请求DTO
 */

@Data
public class DishStatusUpdateRequest {
    @NotNull(message = "菜品状态不能为空")
    private DishStatus status;
}
