package com.luwei.ordering.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum OrderStatus implements CodeEnum{

    PENDING("pending"),
    PAID("paid"),
    CANCELLED("cancelled");

    private final String code;

    OrderStatus(String code){this.code = code;}

    @JsonValue
    public String getCode(){return code;}

    @JsonCreator
    public static OrderStatus fromCode(String code){return CodeEnum.fromCode(OrderStatus.class , code);}
}
