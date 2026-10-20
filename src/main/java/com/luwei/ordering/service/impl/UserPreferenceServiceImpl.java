package com.luwei.ordering.service.impl;

import com.luwei.ordering.common.enums.UserStatus;
import com.luwei.ordering.common.exception.BusinessException;
import com.luwei.ordering.common.exception.ErrorCode;
import com.luwei.ordering.converter.UserPreferenceConverter;
import com.luwei.ordering.dto.request.UserPreferenceRequest;
import com.luwei.ordering.dto.response.UserPreferenceDTO;
import com.luwei.ordering.entity.UserEntity;
import com.luwei.ordering.entity.UserPreferenceEntity;
import com.luwei.ordering.mapper.UserMapper;
import com.luwei.ordering.mapper.UserPreferenceMapper;
import com.luwei.ordering.service.UserPreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Validator;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserPreferenceServiceImpl implements UserPreferenceService {
    private final UserPreferenceMapper userPreferenceMapper;
    private final UserMapper userMapper;
    private final UserPreferenceConverter converter;
    private final Validator validator;

    @Override
    public UserPreferenceDTO queryUserPreference(Long userId) {
        //检查用户状态
        validateActiveUser(userId);

        UserPreferenceEntity entity = userPreferenceMapper.findByUserId(userId);
        if(entity == null) return null;

        return converter.toDTO(entity);
    }

    @Override
    @Transactional
    public UserPreferenceDTO saveUserPreference(Long userId, UserPreferenceRequest req) {
        validateActiveUser(userId);

        if(req == null) throw new BusinessException(ErrorCode.PARAM_ERROR);

        var violations = validator.validate(req);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining("；"));

            throw new BusinessException(ErrorCode.PARAM_ERROR, message);
        }

        UserPreferenceEntity entity = converter.toEntity(userId,req);

        userPreferenceMapper.saveByUserId(entity);

        UserPreferenceEntity saved =
                userPreferenceMapper.findByUserId(userId);

        if (saved == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }

        return converter.toDTO(saved);
    }

    @Override
    public void deleteUserPreference(Long userId) {
        validateActiveUser(userId);
        userPreferenceMapper.deleteByUserId(userId);
    }

    private void validateActiveUser(Long userId){
        if(userId == null || userId <= 0) throw new BusinessException(ErrorCode.PARAM_ERROR);
        UserEntity byId = userMapper.findById(userId);
        if(byId == null) throw new BusinessException(ErrorCode.NOT_EXIST);
        if(byId.getUserStatus() == UserStatus.DISABLED) throw new BusinessException(ErrorCode.USER_DISABLED);
    }
}
