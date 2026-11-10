package com.luwei.ordering.dto.response;
import com.luwei.ordering.dto.response.OrderListItemDTO;
import java.util.List;

public record OrderHistoryToolResult(
        Status status,
        List<OrderListItemDTO> orders,
        Long total, //总订单数
        String message,
        String nextAction
) {
    public enum Status{
        SUCCESS,
        NO_ORDERS,
        INVALID_ARGUMENTS,
        LOGIN_REQUIRED
    }
}