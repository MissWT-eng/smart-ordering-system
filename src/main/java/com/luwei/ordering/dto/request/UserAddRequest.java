package com.luwei.ordering.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UserAddRequest {
    /**
     *  手机号
     */
    @NotBlank
    @Pattern(regexp = "^1[3-9]\\d{9}$" , message = "手机号不符合规则")
    private String phoneNumber;

    /**
     *  用户名称可选
     */
    private String nickName;
}
