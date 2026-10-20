package com.luwei.ordering.dto.response;

import com.luwei.ordering.common.enums.SpicyLevel;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferenceDTO {
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
