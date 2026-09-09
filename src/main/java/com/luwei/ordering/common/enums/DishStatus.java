package com.luwei.ordering.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum DishStatus implements CodeEnum{
    AVAILABLE("available") ,
    UNAVAILABLE("unavailable");

    DishStatus(String code){
        this.code = code;
    }

    private final String code;

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static DishStatus fromCode(String code){
        for(DishStatus d : values())
        {
            if(d.code.equals(code)) return d;
        }
        throw new IllegalArgumentException("未知状态:" + code);
    }
}
