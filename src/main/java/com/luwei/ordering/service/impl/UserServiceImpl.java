package com.luwei.ordering.service.impl;

import com.luwei.ordering.common.enums.UserStatus;
import com.luwei.ordering.common.exception.BusinessException;
import com.luwei.ordering.common.exception.ErrorCode;
import com.luwei.ordering.converter.UserConverter;
import com.luwei.ordering.dto.request.UserAddRequest;
import com.luwei.ordering.dto.response.UserListItemDTO;
import com.luwei.ordering.entity.UserEntity;
import com.luwei.ordering.mapper.UserMapper;
import com.luwei.ordering.service.TokenService;
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
    private final TokenService tokenService;
    @Override
    @Transactional
    public UserListItemDTO identifyByPhone(UserAddRequest request) {
        log.debug("用户识别,入参:{}" , request);
        UserEntity entity = userMapper.findByPhone(request.getPhoneNumber());
        //user 是统一出口变量:老用户指向查到的对象,新用户指向新建的对象
        UserEntity user;

        if (entity != null) {
            //检索到了对应用户,但已被停用则抛出异常
            if(entity.getUserStatus() == UserStatus.DISABLED) throw new BusinessException(ErrorCode.USER_DISABLED);
            user = entity;
        } else {
            //该手机号没有对应的用户,注册一个新用户
            user = userConverter.toEntity(request);
            user.setUserStatus(ACTIVE);

            if(!StringUtils.hasText(user.getNickName()))
            {
                user.setNickName("食客" + user.getPhoneNumber().substring(7));
            }

            userMapper.insertUser(user);
            user = userMapper.findById(user.getUserId());
            log.info("新注册的用户id为:{}, 昵称为:{}, 手机号为:{}" ,
                    user.getUserId() , user.getNickName() , user.getPhoneNumber());
        }

        //统一装配:无论新用户还是老用户,都在这里签发 token(唯一出口)
        UserListItemDTO userListItemDTO = userConverter.toListItemDTO(user);
        userListItemDTO.setToken(tokenService.createToken(user.getUserId()));

        return userListItemDTO;
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
