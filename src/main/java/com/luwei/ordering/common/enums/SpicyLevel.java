package com.luwei.ordering.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum SpicyLevel implements CodeEnum{
    NONE("none"),
    MILD("mild"),
    MEDIUM("medium"),
    HIGH("high");

    private final String code;

    SpicyLevel(String code){
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static SpicyLevel fromCode(String code){
        return CodeEnum.fromCode(SpicyLevel.class , code);
    }

}
