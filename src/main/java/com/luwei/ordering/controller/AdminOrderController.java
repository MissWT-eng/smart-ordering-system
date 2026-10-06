package com.luwei.ordering.controller;

import com.luwei.ordering.dto.request.OrderUpdateRequest;
import com.luwei.ordering.dto.response.ApiResponse;
import com.luwei.ordering.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {
    private final OrderService orderService;

    @PatchMapping
    public ApiResponse<Void> updateOrderStatus(@Valid @RequestBody OrderUpdateRequest request){
        orderService.updateOrder(request);
        return ApiResponse.success();
    }
}
