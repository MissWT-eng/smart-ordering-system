package com.luwei.ordering.service;

import com.luwei.ordering.dto.request.UserAddRequest;
import com.luwei.ordering.dto.response.UserListItemDTO;

public interface UserService {

    UserListItemDTO identifyByPhone(UserAddRequest request);
    UserListItemDTO getById(Long userId);

}
