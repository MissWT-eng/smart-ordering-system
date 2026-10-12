package com.luwei.ordering.dto.response;

import java.time.LocalDateTime;

public record DraftOrderToolResult(
        //草稿编号
        String draftId,
        //草稿过期时间
        LocalDateTime expireAt,
        //返回响应的状态
        Status status,
        //后端根据预算给出的菜品选择，保留编号、名称、单价、数量、小计，是否超出预算等
        DraftOrderResponse draft,
        //推荐摘要、无匹配原因或实际参数错误
        String message,
        //给模型的下一步建议处理
        String nextAction)
    {
        public enum Status{
            SUCCESS,
            BUDGET_EXCEEDED,
            DISH_UNAVAILABLE,
            NO_MATCH,
            INVALID_ARGUMENTS
        }
}
