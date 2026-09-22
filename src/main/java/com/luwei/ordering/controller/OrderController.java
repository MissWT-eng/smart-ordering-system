package com.luwei.ordering.controller;

import com.luwei.ordering.dto.request.OrderCreateRequest;
import com.luwei.ordering.dto.response.ApiResponse;
import com.luwei.ordering.dto.response.OrderListItemDTO;
import com.luwei.ordering.dto.response.PageResult;
import com.luwei.ordering.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor

public class OrderController {
    private final OrderService orderService;

    @PostMapping
    public ApiResponse<OrderListItemDTO> createOrder(@Valid @RequestBody OrderCreateRequest request) {
        return ApiResponse.success(orderService.createOrder(request));
    }

    @GetMapping("/{userId}")
    public ApiResponse<PageResult<OrderListItemDTO>> queryOrders(@PathVariable Long userId,
                                                                 @RequestParam(defaultValue = "1") Integer pageNum,
                                                                 @RequestParam(defaultValue = "10") Integer pageSize) {
        return ApiResponse.success(orderService.queryOrders(userId, pageNum, pageSize));

    }
}