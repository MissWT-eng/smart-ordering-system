package com.luwei.ordering.entity;

import com.luwei.ordering.common.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {
    /**
     *  用户的 id 主键自增
     */
    private Long userId;

    /**
     *  用户手机号
     */
    private String phoneNumber;

    /**
     *  用户昵称
     */
    private String nickName;

    /**
     *  用户创建时间
     */
    private LocalDateTime userCreatedTime;

    /**
     *  用户账户状态
     */
    private UserStatus userStatus;

    /**
     *   用户余额
     */
    private BigDecimal balance;
}
