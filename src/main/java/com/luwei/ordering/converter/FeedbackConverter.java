package com.luwei.ordering.converter;

import com.luwei.ordering.dto.request.FeedbackAddRequest;
import com.luwei.ordering.entity.UserDishFeedbackEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface FeedbackConverter {
    @Mapping(target = "feedbackTime", ignore = true)
    UserDishFeedbackEntity toEntity(FeedbackAddRequest request);
}
