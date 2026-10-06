package com.luwei.ordering.service;

import com.luwei.ordering.dto.request.OrderCreateRequest;
import com.luwei.ordering.dto.request.OrderUpdateRequest;
import com.luwei.ordering.dto.response.OrderListItemDTO;
import com.luwei.ordering.dto.response.PageResult;

public interface OrderService {
    /**
     *  创建订单(事务)
     */
    OrderListItemDTO createOrder(OrderCreateRequest request);

    /**
     *  分页查询用户历史订单(含明细)
     */
    PageResult<OrderListItemDTO> queryOrders(Long userId, Integer pageNum, Integer pageSize);

    /**
     *  更新订单状态
     */
    void updateOrder(OrderUpdateRequest request);
}
