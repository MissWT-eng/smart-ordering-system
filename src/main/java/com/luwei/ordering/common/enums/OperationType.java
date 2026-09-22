package com.luwei.ordering.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum OperationType implements CodeEnum{
    INSERT("insert"),
    UPDATE("update"),
    DELETE("delete");

    private final String code;
    OperationType(String code){
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static OperationType fromCode(String code){
        return CodeEnum.fromCode(OperationType.class , code);
    }
}
