package com.luwei.ordering.dto.response;

import com.luwei.ordering.common.enums.LookupStatus;

public record OrderLookupResponse(
    LookupStatus status,
    OrderListItemDTO order,
    String message
)
{

}
