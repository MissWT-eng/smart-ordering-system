package com.luwei.ordering.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 *菜单模块的菜品请求参数
 */
@Data
public class DishQueryRequest {
    @Min(value = 1 , message = "页码最小为 1")
    private Integer pageNum = 1;

    @Min(value = 1 , message = "每页条数最小为 1")
    @Max(value = 100 , message = "每页条数最大为 100")
    private Integer pageSize = 10;

    private String category;   // 菜品分类（可选）
    private String keyword;    // 菜名/介绍关键字（可选）
    private String status;     // 菜品状态(可选)
}
