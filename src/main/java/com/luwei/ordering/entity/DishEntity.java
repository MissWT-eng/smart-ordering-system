package com.luwei.ordering.entity;

import com.luwei.ordering.common.enums.Category;
import com.luwei.ordering.common.enums.DishStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 菜品实体
 * <p>对应数据库中的 {@code dish} 表，
 * 表示门店可供用户查看、推荐和下单的菜品信息。</p>
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DishEntity {
    /**
     * 菜品编号，数据库主键
     */
    private Long dishNumber;

    /**
     * 菜品名称
     */
    private String dishName;

    /**
     * 菜品状态：available是上架可点 unavailable是下架不可点
     */
    private DishStatus dishStatus;

    /**
     * 菜品介绍，也作为AI检索和推荐的参考内容
     */
    private String dishIntroduction;

    /**
     * 当前菜品价格
     */
    private BigDecimal price;

    /**
     * 菜品分类，例如 meat、vegetable、drink、soup。
     */
    private Category category;
}
