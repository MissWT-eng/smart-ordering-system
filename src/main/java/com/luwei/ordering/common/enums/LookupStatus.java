package com.luwei.ordering.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum LookupStatus implements CodeEnum{
    FOUND("found"),
    NOT_FOUND("not_found");

    private final String code;

    LookupStatus(String code){
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static LookupStatus fromCode(String code){
        return CodeEnum.fromCode(LookupStatus.class , code);
    }
}
