package com.luwei.ordering.converter;


import com.luwei.ordering.dto.request.OrderUpdateRequest;
import com.luwei.ordering.entity.OrdersEntity;
import org.mapstruct.Mapper;



@Mapper(componentModel = "spring")
public interface OrdersConverter {
    OrdersEntity toOrderEntity(OrderUpdateRequest request);
}
