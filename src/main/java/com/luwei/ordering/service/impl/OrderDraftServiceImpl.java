package com.luwei.ordering.service.impl;

import com.luwei.ordering.common.exception.BusinessException;
import com.luwei.ordering.common.exception.ErrorCode;
import com.luwei.ordering.dto.request.DraftOrderRequest;
import com.luwei.ordering.dto.request.OrderCreateRequest;
import com.luwei.ordering.dto.request.OrderItemRequest;
import com.luwei.ordering.dto.response.DraftOrderItemDTO;
import com.luwei.ordering.dto.response.DraftOrderResponse;
import com.luwei.ordering.dto.response.OrderDraftSnapshot;
import com.luwei.ordering.dto.response.OrderListItemDTO;
import com.luwei.ordering.service.OrderDraftService;
import com.luwei.ordering.service.OrderService;
import com.luwei.ordering.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderDraftServiceImpl implements OrderDraftService {
    private final StringRedisTemplate redisTemplate;
    private final RecommendationService recommendationService;
    private final ObjectMapper objectMapper;
    private final OrderService orderService;

    //过期时间
    @Value("${auth.draft-ttl-minutes}")
    private long ttlMinute;

    @Override
    public OrderDraftSnapshot createDraft(DraftOrderRequest req, String ownerKey) {
        if(ownerKey == null || ownerKey.isBlank()) throw new BusinessException(ErrorCode.PARAM_ERROR);
        DraftOrderResponse quote = recommendationService.draftOrder(req);
        String draftId = UUID.randomUUID().toString();
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime expireAt = createdAt.plusMinutes(ttlMinute);

        OrderDraftSnapshot orderDraftSnapshot = new OrderDraftSnapshot(
                draftId,
                ownerKey,
                quote,
                createdAt,
                expireAt
        );

        String json = objectMapper.writeValueAsString(orderDraftSnapshot);
        redisTemplate.opsForValue().set(
                "order:draft:" + draftId,
                json,
                Duration.ofMinutes(ttlMinute)
        );

        return orderDraftSnapshot;
    }

    @Override
    public OrderDraftSnapshot getDraft(String draftId, String ownerKey) {
        //读取 JSON → 还原快照 → 检查归属和有效期 → 返回。
        if (draftId == null || draftId.isBlank()
                || ownerKey == null || ownerKey.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        String json = redisTemplate.opsForValue()
                .get("order:draft:" + draftId);

        if (json == null) {
            throw new BusinessException(
                    ErrorCode.DRAFT_NOT_FOUND_OR_EXPIRED,
                    "草稿不存在或已过期，请重新试算"
            );
        }

        OrderDraftSnapshot snapshot = objectMapper.readValue(json, OrderDraftSnapshot.class);
        if(! ownerKey.equals(snapshot.getOwnerKey())) throw new BusinessException(ErrorCode.FORBIDDEN);
        if(! snapshot.getExpireAt().isAfter(LocalDateTime.now())){
            throw new BusinessException(
                    ErrorCode.DRAFT_NOT_FOUND_OR_EXPIRED,
                    "草稿已过期，请重新试算"
            );
        }
        return snapshot;
    }

    @Override
    public OrderDraftSnapshot validateDraftForConfirmation(String draftId, String ownerKey) {
        OrderDraftSnapshot snapshot = getDraft(draftId, ownerKey);
        if(snapshot.getQuote().getOverBudget() == true){
            throw new BusinessException(ErrorCode.BUDGET_EXCEEDED, "超过预算，请调整后重新试算");
        }
        return snapshot;
    }

    @Override
    public OrderListItemDTO confirmDraft(String draftId, String ownerKey) {
        OrderListItemDTO existingOrder = orderService.findOrderByDraftId(draftId, ownerKey);
        if(existingOrder != null) return existingOrder;

        //校验草稿归属、有效期和预算
        OrderDraftSnapshot snapshot = validateDraftForConfirmation(draftId, ownerKey);
        //核对当前菜品与报价
        validateCurrentQuote(snapshot);
        //组装 OrderCreateRequest
        Long userId = null;
        if(ownerKey.startsWith("user:")){
            userId= Long.valueOf(ownerKey.substring("user:".length()));
        }
        else if (ownerKey.startsWith("guest") );
        else{throw new BusinessException(ErrorCode.NOT_AUTH);}
        OrderCreateRequest request = buildOrderCreateRequest(snapshot, userId);
        //创建订单，返回订单信息
        try{
            return orderService.createOrder(request,snapshot);
        }
        catch (DuplicateKeyException e){
            OrderListItemDTO saveOrder = orderService.findOrderByDraftId(draftId, ownerKey);
            if(saveOrder != null) return saveOrder;
            throw e;
        }
    }


    private OrderCreateRequest buildOrderCreateRequest(
            OrderDraftSnapshot snapshot,
            Long userId) {
        List<DraftOrderItemDTO> items = snapshot.getQuote().getItems();
        List<OrderItemRequest> orderItems = items.stream().map(i -> new OrderItemRequest(i.getDishNumber(), i.getQuantity())).toList();
        return new OrderCreateRequest(userId, orderItems);
    }

    private void validateCurrentQuote(OrderDraftSnapshot snapshot) {
        List<OrderItemRequest> items = snapshot.getQuote()
                                               .getItems()
                                               .stream()
                                               .map(i -> new OrderItemRequest(i.getDishNumber(),i.getQuantity()))
                                               .toList();
        //从草稿里取菜品编号、数量和预算，组装 DraftOrderRequest。
        DraftOrderRequest req = new DraftOrderRequest(items,snapshot.getQuote().getBudget());
        //调用 recommendationService.draftOrder(request)，得到最新报价。菜品不存在或下架时，它会抛出异常。
        DraftOrderResponse response = recommendationService.draftOrder(req);
        //按菜品编号，对比草稿与最新报价的每项单价。
        Map<Long, DraftOrderItemDTO> oldItemsByNumber = new HashMap<>();

        for (DraftOrderItemDTO oldItem : snapshot.getQuote().getItems()) {
            oldItemsByNumber.put(oldItem.getDishNumber(), oldItem);
        }

        for (DraftOrderItemDTO currentItem : response.getItems()) {
            DraftOrderItemDTO oldItem =
                    oldItemsByNumber.get(currentItem.getDishNumber());

            if (oldItem == null) {
                throw new BusinessException(
                        ErrorCode.PARAM_ERROR,
                        "草稿菜品信息不一致，请重新试算");
            }
            //单价变化时，抛出业务异常，提示“菜品价格发生变化，请重新试算并确认”。
            if (oldItem.getUnitPrice()
                    .compareTo(currentItem.getUnitPrice()) != 0) {
                throw new BusinessException(
                        ErrorCode.DRAFT_DISH_INFO_ERROR,
                        "菜品价格发生变化，请重新试算并确认");
            }
        }
    }

}
