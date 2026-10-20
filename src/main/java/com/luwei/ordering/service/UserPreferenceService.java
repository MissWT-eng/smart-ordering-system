package com.luwei.ordering.service;

import com.luwei.ordering.dto.request.UserPreferenceRequest;
import com.luwei.ordering.dto.response.UserPreferenceDTO;

public interface UserPreferenceService {
    /**
     *  查看用户画像
     */
    UserPreferenceDTO queryUserPreference(Long userId);

    /**
     *  更新、修改用户画像
     */
    UserPreferenceDTO saveUserPreference(Long userId, UserPreferenceRequest req);

    /**
     *  删除用户画像
     */
    void deleteUserPreference(Long userId);
}
