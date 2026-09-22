package com.luwei.ordering.service.impl;


import com.luwei.ordering.common.enums.DishStatus;
import com.luwei.ordering.common.enums.OrderStatus;
import com.luwei.ordering.common.enums.UserStatus;
import com.luwei.ordering.common.exception.BusinessException;
import com.luwei.ordering.common.exception.ErrorCode;
import com.luwei.ordering.dto.request.OrderCreateRequest;
import com.luwei.ordering.dto.request.OrderItemRequest;
import com.luwei.ordering.dto.response.OrderItemDTO;
import com.luwei.ordering.dto.response.OrderListItemDTO;
import com.luwei.ordering.dto.response.PageResult;
import com.luwei.ordering.entity.DishEntity;
import com.luwei.ordering.entity.OrderItemEntity;
import com.luwei.ordering.entity.OrdersEntity;
import com.luwei.ordering.entity.UserEntity;
import com.luwei.ordering.mapper.DishMapper;
import com.luwei.ordering.mapper.OrderItemMapper;
import com.luwei.ordering.mapper.OrderMapper;
import com.luwei.ordering.mapper.UserMapper;
import com.luwei.ordering.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

@Slf4j
@Service
@RequiredArgsConstructor

public class OrderServiceImpl implements OrderService {
    private final UserMapper userMapper;
    private final DishMapper dishMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    @Override
    @Transactional
    public OrderListItemDTO createOrder(OrderCreateRequest request) {

        //做用户存在校验
        UserEntity entity = userMapper.findById(request.getUserId());
        if (entity == null) throw new BusinessException(ErrorCode.NOT_EXIST);
        //再做一个用户状态校验
        if (entity.getUserStatus() == UserStatus.DISABLED) throw new BusinessException(ErrorCode.USER_DISABLED);

        //收集菜号,一次反查
        List<Long> dishNumber = request.getOrderItems().stream()
                .map(OrderItemRequest::getDishNumber)
                .toList();

        List<DishEntity> dishes = dishMapper.findByIds(dishNumber);
        Map<Long, DishEntity> dishMap = dishes.stream().collect(toMap(DishEntity::getDishNumber, d -> d));

        for (OrderItemRequest item : request.getOrderItems()) {
            DishEntity dish = dishMap.get(item.getDishNumber());
            if (dish == null || dish.getDishStatus() == DishStatus.UNAVAILABLE) {
                log.warn("菜品不可下单, dishNumber:{}", item.getDishNumber());
                throw new BusinessException(ErrorCode.DISH_NOT_AVAILABLE);
            }
        }

        //算总额,用数据库价格
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemRequest item : request.getOrderItems()) {
            BigDecimal price = dishMap.get(item.getDishNumber()).getPrice();
            total = total.add(price.multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        //插主表,回填orderNum
        OrdersEntity order = new OrdersEntity();
        order.setOrderUserId(request.getUserId());
        order.setOrderStatus(OrderStatus.PENDING);
        order.setOrderTotalMoney(total);

        orderMapper.insertOrder(order);

        //组明细(绑定orderNum + 价格快照) 批量插入
        List<OrderItemEntity> itemEntities = new ArrayList<>();

        for (OrderItemRequest item : request.getOrderItems()) {
            DishEntity dish = dishMap.get(item.getDishNumber());

            OrderItemEntity itemEntity = new OrderItemEntity();
            itemEntity.setOrderNum(order.getOrderNum());
            itemEntity.setQuantity(item.getQuantity());
            itemEntity.setDishNumber(item.getDishNumber());
            itemEntity.setPriceAtTime(dish.getPrice());

            itemEntities.add(itemEntity);
        }

        orderItemMapper.insertBatch(itemEntities);

        //组装返回
        List<OrderItemDTO> itemDTOS = new ArrayList<>();
        for (OrderItemEntity e : itemEntities) {
            itemDTOS.add(new OrderItemDTO(
                    e.getDishNumber(),
                    dishMap.get(e.getDishNumber()).getDishName(),
                    e.getQuantity(),
                    e.getPriceAtTime()
            ));
        }

        return new OrderListItemDTO(
                order.getOrderNum(),
                LocalDateTime.now(),
                OrderStatus.PENDING,
                order.getOrderTotalMoney(),
                itemDTOS
        );
    }

    @Override
    public PageResult<OrderListItemDTO> queryOrders(Long userId, Integer pageNum, Integer pageSize) {
        //做用户存在校验
        UserEntity entity = userMapper.findById(userId);
        if (entity == null) throw new BusinessException(ErrorCode.NOT_EXIST);
        //再做一个用户状态校验
        if (entity.getUserStatus() == UserStatus.DISABLED) {
            log.warn("当前查询历史订单记录只对注册用户开发");
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }

        //查出总数
        long total = orderMapper.countByUserId(userId);

        //算offset
        long offset = (pageNum - 1) * pageSize;
        List<OrdersEntity> orders = orderMapper.findByUserId(userId, offset, pageSize);

        //空页提前返回(总页数正常算,数据是空列表)
        int totalPages = (int) Math.ceil((double) total / pageSize);

        //收集本页全部dishNumber,逐单查明
        Map<Long, List<OrderItemEntity>> itemByOrder = new HashMap<>();
        for (OrdersEntity order : orders) {
            itemByOrder.put(order.getOrderNum(), orderItemMapper.findByOrderNum(order.getOrderNum()));
        }

        //收集全部菜号,一键反查,构Map<dishNumber , String dishName>
        Set<Long> allDishNumber = orders.stream().flatMap(order -> itemByOrder.get(order.getOrderNum()).stream())
                .map(OrderItemEntity::getDishNumber)
                .collect(Collectors.toSet());
        Map<Long, String> nameMap = dishMapper.findByIds(new ArrayList<>(allDishNumber))
                .stream()
                .collect(Collectors.toMap(DishEntity::getDishNumber, DishEntity::getDishName));
        //组装 每单明细从itemByOrder中取 菜名从nameMap
        List<OrderListItemDTO> list = new ArrayList<>();
        for (OrdersEntity order : orders) {
            List<OrderItemDTO> itemDTOS = new ArrayList<>();
            for (OrderItemEntity e : itemByOrder.get(order.getOrderNum())) {
                itemDTOS.add(new OrderItemDTO(
                        e.getDishNumber(),
                        nameMap.get(e.getDishNumber()),
                        e.getQuantity(),
                        e.getPriceAtTime()));
            }
            list.add(new OrderListItemDTO(
                    order.getOrderNum(),
                    order.getOrderTime(),
                    order.getOrderStatus(),
                    order.getOrderTotalMoney(),
                    itemDTOS));
        }
        return new PageResult<>(total, pageNum, pageSize, list, totalPages);
    }
}