package com.luwei.ordering.converter;
import com.luwei.ordering.dto.request.UserAddRequest;
import com.luwei.ordering.dto.response.UserListItemDTO;
import com.luwei.ordering.entity.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;


@Mapper(componentModel = "spring")
public interface UserConverter {

    UserListItemDTO toListItemDTO(UserEntity entity);

    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "userStatus", ignore = true)
    UserEntity toEntity(UserAddRequest request);

}
