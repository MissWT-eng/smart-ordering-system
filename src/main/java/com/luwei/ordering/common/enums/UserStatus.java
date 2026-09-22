package com.luwei.ordering.common.enums;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum UserStatus implements CodeEnum{
    ACTIVE("active"),
    DISABLED("disabled");

    UserStatus(String code)
    {
        this.code = code;
    }

    private final String code;


    @JsonValue
    public String getCode()
    {
        return code;
    }

    @JsonCreator
    public static UserStatus fromCode(String code){
        return CodeEnum.fromCode(UserStatus.class , code);
    }
}
