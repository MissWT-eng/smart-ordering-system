package com.luwei.ordering.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationLogEntity {
    /**
     * 推荐记录 id
     */
    private Long logId;

    /**
     * 接收推荐的用户
     */
    private Long userId;

    /**
     *  Agent推荐的菜品id号(JSON)
     */
    private String recommendedDishes;

    /**
     *  用户最终实际选择的菜品id(JSON) 目前为null,后续在订单的模块进行完善
     *  role: 之后可以配合recommendedDishes优化推荐
     */
    private String orderedDishes;

    /**
     *   天气，目前唯一一个外部会影响推荐的因素,后续可加其他因素,phase4做
     */
    private String weather;

    /**
     *  生成的文本的内容(JSON)
     */
    private String context;

    /**
     *   推荐的时间
     */
    private LocalDateTime recommendationTime;

    /**
     *  用户的反馈分数,phase4做,目前为null
     */
    private Integer feedbackScore;
}
