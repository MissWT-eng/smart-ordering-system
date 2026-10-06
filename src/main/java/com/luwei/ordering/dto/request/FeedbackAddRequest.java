package com.luwei.ordering.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
/**
 *  反馈分数请求体
 */
@Data
public class FeedbackAddRequest {
    @NotNull(message = "用户id不能为空")
    private Long userId;

    @NotNull(message = "菜品编号不能为空")
    private Long dishNumber;

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分最小为1")
    @Max(value = 5, message = "评分最大为5")
    private Integer feedbackScore;
}
