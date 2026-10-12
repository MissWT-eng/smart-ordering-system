package com.luwei.ordering.dto.response;



public record ConfirmOrderToolResult(
        ConfirmOrderToolResult.Status status,
        //返回最终确认的订单
        OrderListItemDTO order,
        //推荐摘要、无匹配原因或实际参数错误
        String message,
        //给模型的下一步建议处理
        String nextAction)
    {
        public enum Status{
            SUCCESS,
            NO_MATCH,
            CONFIRMATION_REQUIRED,
            FORBIDDEN,
            CONFIRMATION_MISMATCH,
            BUDGET_EXCEEDED,
            DISH_NOT_AVAILABLE,
            INVALID_ARGUMENTS
        }
}
