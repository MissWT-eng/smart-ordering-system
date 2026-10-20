package com.luwei.ordering.converter;

import com.luwei.ordering.dto.request.UserPreferenceRequest;
import com.luwei.ordering.dto.response.UserPreferenceDTO;
import com.luwei.ordering.dto.response.UserPreferenceProposalDTO;
import com.luwei.ordering.entity.UserPreferenceEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserPreferenceConverter {
    UserPreferenceDTO toDTO(UserPreferenceEntity entity);

    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "spicyLevel", source = "req.spicyLevel")
    @Mapping(target = "preferenceText", source = "req.preferenceText")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    UserPreferenceEntity toEntity(Long userId, UserPreferenceRequest req);

}
