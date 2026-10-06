package com.luwei.ordering.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Set;

public enum OrderStatus implements CodeEnum{
    PAID("paid", Set.of()),
    CANCELLED("cancelled", Set.of()),
    PENDING("pending" , Set.of(PAID,CANCELLED));

    private final String code;
    private final Set<OrderStatus> allowedTargets;

    OrderStatus(String code, Set<OrderStatus> allowedTargets){
        this.code = code;
        this.allowedTargets = allowedTargets;
    }

    @JsonValue
    public String getCode(){return code;}

    @JsonCreator
    public static OrderStatus fromCode(String code){return CodeEnum.fromCode(OrderStatus.class , code);}

    public boolean canTransitionTo(OrderStatus orderStatus){
        return allowedTargets.contains(orderStatus);
    }

}
