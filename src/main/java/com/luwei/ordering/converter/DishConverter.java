package com.luwei.ordering.converter;

import com.luwei.ordering.dto.request.DishAddRequest;
import com.luwei.ordering.dto.request.DishUpdateRequest;
import com.luwei.ordering.dto.response.DishListItemDTO;
import com.luwei.ordering.entity.DishEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface DishConverter {
    DishListItemDTO toListItemDTO(DishEntity dishEntity);

    @Mapping(target = "dishNumber" , ignore = true)
    @Mapping(target = "dishStatus" , ignore = true)
    DishEntity toEntity(DishAddRequest request);

    @Mapping(target = "dishNumber" , ignore = true)
    @Mapping(target = "dishStatus" , ignore = true)
    void updateEntity(DishUpdateRequest request , @MappingTarget DishEntity dishEntity);

}
