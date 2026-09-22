package com.luwei.ordering.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDishFeedbackEntity {
    /**
     *  反馈 id
     */
    private Long feedbackId;

    /**
     *  用户 id
     */
    private Long userId;

    /**
     *  菜品编号
     */
    private Long dishNumber;

    /**
     *  用户反馈分数
     */
    private Integer feedbackScore;

    /**
     *  反馈时间
     */
    private LocalDateTime feedbackTime;
}
