package com.luwei.ordering.dto.internal;

import lombok.Data;

@Data
public class DishFeedbackStat {
    private Long dishNumber;

    //平均分 1 ~ 5
    private Double avgScore;

    //点单次数
    private Long orderCount;
}
