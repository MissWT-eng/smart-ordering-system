package com.luwei.ordering.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 *  用户推荐的请求参数
 */
@Data
public class RecommendationRequest {

    //  推荐的用户id
    @NotNull
    private Long userId;

    //  用户的喜好或者饮食习惯
    @NotBlank
    @Size(max = 200)
    private String preference;
}
