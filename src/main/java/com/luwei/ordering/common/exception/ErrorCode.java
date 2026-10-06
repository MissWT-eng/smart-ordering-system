package com.luwei.ordering.common.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    SUCCESS(0 , "success"),
    PARAM_ERROR(400 ,"参数错误"),
    NOT_EXIST(404 , "访问资源不存在"),
    SYSTEM_ERROR(500 , "系统内部错误"),
    DISH_NAME_EXISTS(409 , "菜品名已存在"),
    USER_DISABLED(403 , "该用户已被禁用"),
    DISH_NOT_AVAILABLE(409 , "该菜品不存在或已下架"),
    ILLEGAL_QUANTITY(400, "菜品数量不合法"),
    CANT_TRANSITION_TO(409,"订单状态不允许改变"),
    NOT_AUTH(401, "未授权用户"),
    FORBIDDEN(403, "凭证与身份不符");
    private final int code;
    private final String msg;

    ErrorCode(int code, String msg){
        this.msg =msg;
        this.code = code;
    }
}
