package com.luwei.ordering.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

/**
 *   菜品推荐接口返回参数
 */

@Data
@AllArgsConstructor
public class RecommendationResponse {

    //  最终返回推荐菜品
    private List<RecommendedDishDTO> dishes;
    //  推荐总理由
    private String summary;
    //本次目标推荐数量，包括未指定时采用的默认值
    private Integer targetCount;

}
