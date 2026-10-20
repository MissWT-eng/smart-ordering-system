package com.luwei.ordering.entity;

import com.luwei.ordering.common.enums.SpicyLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferenceEntity {
    /**
     *  用户id
     */
    private Long userId;

    /**
     *  用户口味偏好
     */
    private String preferenceText;

    /**
     *  用户口味辣度记录
     */
    private SpicyLevel spicyLevel;

    /**
     *  记录创建时间
     */
    private LocalDateTime createdAt;

    /**
     *  记录更新时间
     */
    private LocalDateTime updatedAt;
}
