package com.luwei.ordering.service;

import com.luwei.ordering.dto.request.OrderCreateRequest;
import com.luwei.ordering.dto.request.OrderUpdateRequest;
import com.luwei.ordering.dto.response.OrderDraftSnapshot;
import com.luwei.ordering.dto.response.OrderListItemDTO;
import com.luwei.ordering.dto.response.PageResult;

public interface OrderService {
    /**
     *  创建订单(事务)
     */
    OrderListItemDTO createOrder(OrderCreateRequest request);

    /**
     *  创建订单(重载)
     */
    OrderListItemDTO createOrder(
            OrderCreateRequest request,
            OrderDraftSnapshot snapshot);

    /**
     *  分页查询用户历史订单(含明细)
     */
    PageResult<OrderListItemDTO> queryOrders(Long userId, Integer pageNum, Integer pageSize);

    /**
     *  更新订单状态
     */
    void updateOrder(OrderUpdateRequest request);
    /**
     *  查询草稿订单
     */
    OrderListItemDTO findOrderByDraftId(String draftId, String ownerKey);
}
