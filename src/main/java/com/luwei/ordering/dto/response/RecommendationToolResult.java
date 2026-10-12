package com.luwei.ordering.dto.response;
import java.util.List;

public record RecommendationToolResult(
        //明确结果状态
        Status status,
        //后端给出的候选菜品，保留编号，单价，理由等
        List<RecommendedDishDTO> dishes,
        //推荐摘要、无匹配原因或实际参数错误
        String message,
        //给模型的下一步建议处理
        String nextAction
)

{
    public enum Status{
        SUCCESS,
        NO_MATCH,
        INVALID_ARGUMENTS
    }

}
