package com.luwei.ordering.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 *  用户推荐的请求参数
 */
@Data
public class RecommendationRequest {
    //  推荐的用户id 可以为空,为游客推荐菜品
    private Long userId;

    //  用户的喜好或者饮食习惯
    @NotBlank
    @Size(max = 200)
    private String preference;

    @Max(value = 100, message = "最大点餐人数不能超过100人")
    @Min(value = 1, message = "最小点餐人数不能小于1人")
    //  (可选)用户点餐的人数
    private Integer peopleNum;

    @Min(value = 0, message = "预算不能小于0元")
    //  (可选)用户的预算金额
    private BigDecimal budget;

}
