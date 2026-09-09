package com.luwei.ordering.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Category implements CodeEnum{
    MEAT("meat"),
    VEGETABLE("vegetable"),
    DRINK("drink"),
    SOUP("soup");

    private final String code;
    Category(String code){
        this.code = code;

    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static Category fromCode(String code){
        for(Category c : values())
        {
            if(c.code.equals(code)) return c;
        }
        throw new IllegalArgumentException("未知分类:" + code);
    }
}
