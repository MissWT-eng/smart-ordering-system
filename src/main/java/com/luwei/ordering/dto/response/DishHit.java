package com.luwei.ordering.dto.response;

/**
 * 召回命中：菜品编号 + 命中次数（热销次数或本人点过的次数）
 */
public record DishHit(Long dishNumber, Integer cnt) {
}
