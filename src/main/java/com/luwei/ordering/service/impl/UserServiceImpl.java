package com.luwei.ordering.service.impl;

import com.luwei.ordering.common.enums.UserStatus;
import com.luwei.ordering.common.exception.BusinessException;
import com.luwei.ordering.common.exception.ErrorCode;
import com.luwei.ordering.converter.UserConverter;
import com.luwei.ordering.dto.request.UserAddRequest;
import com.luwei.ordering.dto.response.UserListItemDTO;
import com.luwei.ordering.entity.UserEntity;
import com.luwei.ordering.mapper.UserMapper;
import com.luwei.ordering.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import static com.luwei.ordering.common.enums.UserStatus.ACTIVE;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    private final UserConverter userConverter;
    @Override
    @Transactional
    public UserListItemDTO identifyByPhone(UserAddRequest request) {
        log.debug("用户识别,入参:{}" , request);
        UserEntity entity = userMapper.findByPhone(request.getPhoneNumber());

        if (entity != null)
        {
            //做检索,如果检索到了对应用户,但是用户已被停用抛出异常
            if(entity.getUserStatus() == UserStatus.DISABLED)
            {
                throw new BusinessException(ErrorCode.USER_DISABLED);
            }

            //如果检索到了对应用户返回结果
            return userConverter.toListItemDTO(entity);
        }

        //如果该手机号没有对应的用户，直接注册一个对应的用户
        entity = userConverter.toEntity(request);
        entity.setUserStatus(ACTIVE);

        if(!StringUtils.hasText(entity.getNickName()))
        {
            entity.setNickName("食客" + entity.getPhoneNumber().substring(7));
        }

        userMapper.insertUser(entity);
        entity = userMapper.findById(entity.getUserId());
        log.info("新注册的用户id为:{}, 昵称为:{}, 手机号为:{}" ,
                entity.getUserId() , entity.getNickName() , entity.getPhoneNumber());
        return userConverter.toListItemDTO(entity);

    }

    @Override
    @Transactional(readOnly = true)
    public UserListItemDTO getById(Long userId) {
        //查找，做存在性校验
        UserEntity entity = userMapper.findById(userId);
        if(entity == null) throw new BusinessException(ErrorCode.NOT_EXIST);
        //转化返回DTO对象
        log.info("获取到的用户id为:{}, 昵称为:{}, 手机号为:{}" ,
                  entity.getUserId() ,
                  entity.getNickName() ,
                  entity.getPhoneNumber());
        return userConverter.toListItemDTO(entity);
    }

}
