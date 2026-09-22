package com.luwei.ordering.controller;

import com.luwei.ordering.dto.request.UserAddRequest;
import com.luwei.ordering.dto.response.ApiResponse;
import com.luwei.ordering.dto.response.UserListItemDTO;
import com.luwei.ordering.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 *  用户接口
 */

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {
    private final UserService userService;

    /**
     *  手机号识别用户
     */
    @PostMapping("/identify")
    public ApiResponse<UserListItemDTO> identifyByPhone(@Valid @RequestBody
                                                        UserAddRequest request)
    {
        return ApiResponse.success(userService.identifyByPhone(request));
    }

    /**
     *  通过用户Id获取用户信息
     */
    @GetMapping("/{userId}")
    public ApiResponse<UserListItemDTO> getById(@PathVariable Long userId)
    {
        return ApiResponse.success(userService.getById(userId));
    }
}
