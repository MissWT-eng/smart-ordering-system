package com.luwei.ordering.dto.request;

import com.luwei.ordering.common.enums.Category;
import com.luwei.ordering.common.enums.SpicyLevel;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;

/**
 *菜单模块的添加请求参数
 */
@Data
public class DishAddRequest {
    /**
     * 菜品名称
     */
    @NotBlank
    @Size(max = 100 , message = "菜品名不能超过100字")
    private String dishName;

    /**
     *菜品介绍
     */
    @Size(max = 2000 , message = "菜品介绍不能超过2000字")
    private String dishIntroduction;

    /**
     * 菜品价格
     * */
    @NotNull
    @DecimalMin(value = "0" , message = "菜品价格不能低于0元")
    @Digits(integer = 8 , fraction = 2 , message = "菜品价格最高整数为8位,小数位最多2位")
    private BigDecimal price;

    /**
     * 菜品类别
     */
    @NotNull
    private Category category;

    /**
     *  菜品辣度
     */
    @NotNull
    private SpicyLevel spicyLevel;
}
