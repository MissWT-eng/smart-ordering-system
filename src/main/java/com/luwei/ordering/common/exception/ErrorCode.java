package com.luwei.ordering.common.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    SUCCESS(0 , "success"),
    PARAM_ERROR(400 ,"参数错误"),
    NOT_EXIST(404 , "访问资源不存在"),
    SYSTEM_ERROR(500 , "系统内部错误"),
    DISH_NAME_EXISTS(409 , "菜品名已存在");

    private final int code;
    private final String msg;

    ErrorCode(int code, String msg){
        this.msg =msg;
        this.code = code;
    }
}
