package com.luwei.ordering.dto.response;
import java.math.BigDecimal;
import java.util.List;

public record RecommendationToolResult(
        //明确结果状态
        Status status,
        //后端给出的候选菜品，保留编号，单价，理由等
        List<RecommendedDishDTO> dishes,
        //推荐摘要、无匹配原因或实际参数错误
        String message,
        //给模型的下一步建议处理
        String nextAction,
        //本次目标推荐数量，尚未取得有效值时为空
        Integer targetCount,
        //目前避免不了模型心算价格的问题，直接给出一个根据单个菜品价格计算的总价，避免模型心算价格
        BigDecimal unitPriceSum
)

{
    public enum Status{
        SUCCESS,
        NO_MATCH,
        SERVICE_UNAVAILABLE,
        INVALID_ARGUMENTS
    }

}
