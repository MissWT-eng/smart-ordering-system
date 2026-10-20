package com.luwei.ordering.controller;

import com.luwei.ordering.common.exception.BusinessException;
import com.luwei.ordering.common.exception.ErrorCode;
import com.luwei.ordering.dto.request.UserPreferenceRequest;
import com.luwei.ordering.dto.response.ApiResponse;
import com.luwei.ordering.dto.response.UserPreferenceDTO;
import com.luwei.ordering.service.TokenService;
import com.luwei.ordering.service.UserPreferenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users/me/preference")
public class UserPreferenceController {
    private final TokenService tokenService;
    private final UserPreferenceService userPreferenceService;

    @GetMapping
    public ApiResponse<UserPreferenceDTO> getUserPreference(@RequestHeader("X-User-Token") String token)
    {
        Long userId = resolveUserId(token);
        return ApiResponse.success(userPreferenceService.queryUserPreference(userId));
     }

    @PutMapping
    public ApiResponse<UserPreferenceDTO> saveUserPreference(@RequestHeader("X-User-Token") String token,
                                                             @Valid @RequestBody UserPreferenceRequest req)
    {
        Long userId = resolveUserId(token);
        return ApiResponse.success(
                userPreferenceService.saveUserPreference(userId, req));
    }

     @DeleteMapping
     public ApiResponse<Void> deleteUserPreference(@RequestHeader("X-User-Token") String token)
     {
        Long userId = resolveUserId(token);
        userPreferenceService.deleteUserPreference(userId);
        return ApiResponse.success();
     }

    private Long resolveUserId(String token){
        Long userId = tokenService.resolve(token);
        if(userId == null) throw new BusinessException(ErrorCode.NOT_AUTH);
        return userId;
    }
}
