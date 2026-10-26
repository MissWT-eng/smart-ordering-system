package com.luwei.ordering.service.impl;

import com.luwei.ordering.common.exception.BusinessException;
import com.luwei.ordering.common.exception.ErrorCode;
import com.luwei.ordering.concurrent.OrderDraftOperationGuard;
import com.luwei.ordering.dto.request.DraftOrderRequest;
import com.luwei.ordering.dto.request.OrderCreateRequest;
import com.luwei.ordering.dto.request.OrderItemRequest;
import com.luwei.ordering.dto.request.ReviseDraftRequest;
import com.luwei.ordering.dto.response.DraftOrderItemDTO;
import com.luwei.ordering.dto.response.DraftOrderResponse;
import com.luwei.ordering.dto.response.OrderDraftSnapshot;
import com.luwei.ordering.dto.response.OrderListItemDTO;
import com.luwei.ordering.service.OrderDraftService;
import com.luwei.ordering.service.OrderService;
import com.luwei.ordering.service.RecommendationService;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderDraftServiceImpl implements OrderDraftService {
    private final StringRedisTemplate redisTemplate;
    private final RecommendationService recommendationService;
    private final ObjectMapper objectMapper;
    private final OrderService orderService;
    private final Validator validator;
    private final OrderDraftOperationGuard guard;
    private static final String REPLACE_DRAFT_LUA = """
        -- 检查参数：新旧 Key 必须不同，新有效期必须是正整数
        local newTtl = tonumber(ARGV[4])
        if KEYS[1] == KEYS[2]
                or not newTtl
                or newTtl <= 0
                or newTtl % 1 ~= 0 then
            return -3
        end

        -- 1. 重新读取旧草稿
        local raw = redis.call('GET', KEYS[1])
        if not raw then
            return 0
        end

        -- 获取旧草稿剩余有效期
        local oldTtl = redis.call('PTTL', KEYS[1])
        if oldTtl == -1 then
            return -3
        end
        if oldTtl <= 0 then
            return 0
        end

        -- 2. 解析并检查旧草稿
        local ok, oldDraft = pcall(cjson.decode, raw)
        if not ok or type(oldDraft) ~= 'table' then
            return -3
        end

        if oldDraft.ownerKey ~= ARGV[1] then
            return -1
        end

        -- JSON null 对应 cjson.null，需要单独判断
        if oldDraft.replacedByDraftId ~= nil
                and oldDraft.replacedByDraftId ~= cjson.null then
            return -2
        end

        -- 3. 检查 Java 准备的新旧 JSON
        local newOk, newDraft = pcall(cjson.decode, ARGV[2])
        local markedOk, markedOld = pcall(cjson.decode, ARGV[3])

        if not newOk or type(newDraft) ~= 'table'
                or not markedOk or type(markedOld) ~= 'table' then
            return -3
        end

        if type(newDraft.draftId) ~= 'string'
                or newDraft.draftId == ''
                or KEYS[2] ~= 'order:draft:' .. newDraft.draftId
                or newDraft.ownerKey ~= ARGV[1]
                or markedOld.draftId ~= oldDraft.draftId
                or markedOld.ownerKey ~= oldDraft.ownerKey
                or markedOld.replacedByDraftId ~= newDraft.draftId then
            return -3
        end

        if newDraft.replacedByDraftId ~= nil
                and newDraft.replacedByDraftId ~= cjson.null then
            return -3
        end

        -- 防止覆盖已有草稿
        if redis.call('EXISTS', KEYS[2]) == 1 then
            return -3
        end

        -- 4. 校验全部通过，再执行写入
        redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[4])
        redis.call('SET', KEYS[1], ARGV[3], 'PX', oldTtl)

        return 1
        """;

    //过期时间
    @Value("${auth.draft-ttl-minutes}")
    private long ttlMinute;

    @Override
    public OrderDraftSnapshot createDraft(DraftOrderRequest req, String ownerKey) {
        if(ownerKey == null || ownerKey.isBlank()) throw new BusinessException(ErrorCode.PARAM_ERROR);
        DraftOrderResponse quote = recommendationService.draftOrder(req);
        OrderDraftSnapshot orderDraftSnapshot = buildDraftSnapshot(quote, ownerKey);

        String json = objectMapper.writeValueAsString(orderDraftSnapshot);
        redisTemplate.opsForValue().set(
                "order:draft:" + orderDraftSnapshot.getDraftId(),
                json,
                Duration.ofMinutes(ttlMinute)
        );

        return orderDraftSnapshot;
    }


    private OrderDraftSnapshot buildDraftSnapshot(DraftOrderResponse quote,String ownerKey){

        String draftId = UUID.randomUUID().toString();
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime expireAt = createdAt.plusMinutes(ttlMinute);

        OrderDraftSnapshot orderDraftSnapshot = new OrderDraftSnapshot(
                draftId,
                ownerKey,
                quote,
                null,
                createdAt,
                expireAt
        );

        return orderDraftSnapshot;
    }

    private void replaceDraftAtomically(OrderDraftSnapshot original, OrderDraftSnapshot revised) {
        if (original == null || revised == null
                || original.getDraftId() == null || original.getDraftId().isBlank()
                || revised.getDraftId() == null || revised.getDraftId().isBlank()
                || original.getOwnerKey() == null || original.getOwnerKey().isBlank()
                || !original.getOwnerKey().equals(revised.getOwnerKey())
                || original.getDraftId().equals(revised.getDraftId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        if (original.getExpireAt() == null || revised.getExpireAt() == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }

        // 新草稿按快照中的到期时间设置 TTL，避免准备 JSON 的耗时延长有效期。
        LocalDateTime now = LocalDateTime.now();
        long newTtlMillis = Duration.between(now, revised.getExpireAt()).toMillis();
        if (!original.getExpireAt().isAfter(now) || newTtlMillis <= 0) {
            throw new BusinessException(ErrorCode.DRAFT_NOT_FOUND_OR_EXPIRED);
        }

        // 只修改副本；Lua 拒绝替换时，原对象和 Redis 中的旧草稿均不被提前改动。
        OrderDraftSnapshot markedOriginal = new OrderDraftSnapshot(
                original.getDraftId(),
                original.getOwnerKey(),
                original.getQuote(),
                revised.getDraftId(),
                original.getCreatedAt(),
                original.getExpireAt()
        );

        String newJson = objectMapper.writeValueAsString(revised);
        String markedOriginalJson = objectMapper.writeValueAsString(markedOriginal);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(REPLACE_DRAFT_LUA, Long.class);
        Long result = redisTemplate.execute(
                script,
                // KEYS[1] 是旧草稿 Key，KEYS[2] 是新草稿 Key。
                List.of("order:draft:" + original.getDraftId(), "order:draft:" + revised.getDraftId()),
                // ARGV[1..4]：归属凭证、新草稿 JSON、标记后的旧草稿 JSON、新 TTL（毫秒）。
                original.getOwnerKey(),
                newJson,
                markedOriginalJson,
                String.valueOf(newTtlMillis)
        );

        // Lua 的数字返回值只用于内部判断，由 Java 转换为明确的业务异常。
        if (result == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
        if (result == 1L) {
            return;
        }
        if (result == 0L) {
            throw new BusinessException(ErrorCode.DRAFT_NOT_FOUND_OR_EXPIRED);
        }
        if (result == -1L) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (result == -2L) {
            throw new BusinessException(ErrorCode.DRAFT_REPLACED);
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR);
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

        if(snapshot.getReplacedByDraftId() != null){
            throw new BusinessException(ErrorCode.DRAFT_REPLACED);
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
        if(draftId == null || draftId.isBlank() || ownerKey == null || ownerKey.isBlank()){
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        if(! guard.tryAcquire(draftId)){
            throw new BusinessException(ErrorCode.DRAFT_BUSY);
        }
        try{
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
        finally {
            guard.release(draftId);
        }
    }

    @Override
    public OrderDraftSnapshot reviseDraft(String draftId, ReviseDraftRequest req, String ownerKey) {
        if(draftId == null || draftId.isBlank() || ownerKey == null || ownerKey.isBlank()){
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        if(! guard.tryAcquire(draftId)){
            throw new BusinessException(ErrorCode.DRAFT_BUSY);
        }

        try {
            if (req == null) throw new BusinessException(ErrorCode.PARAM_ERROR);
            var violations = validator.validate(req);
            if (!violations.isEmpty()) {
                String message = violations.stream()
                        .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                        .sorted()
                        .collect(Collectors.joining("；"));

                throw new BusinessException(ErrorCode.PARAM_ERROR, message);
            }

            OrderDraftSnapshot original = getDraft(draftId, ownerKey);

            OrderListItemDTO order = orderService.findOrderByDraftId(draftId, ownerKey);
            if (order != null) {
                throw new BusinessException(ErrorCode.DRAFT_ALREADY_CONFIRMED);
            }

            Set<Long> originalNumbers = original.getQuote().getItems()
                    .stream()
                    .map(DraftOrderItemDTO::getDishNumber)
                    .collect(Collectors.toSet());
            Set<Long> submittedNumbers = new HashSet<>();
            for (OrderItemRequest orderItem : req.getOrderItems()) {
                if (!originalNumbers.contains(orderItem.getDishNumber())) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR, "不能添加原草稿中没有的菜品号");
                }
                if (!submittedNumbers.add(orderItem.getDishNumber())) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR, "不能重复添加同一种菜品");
                }
            }

            DraftOrderRequest request = new DraftOrderRequest(
                    req.getOrderItems(),
                    original.getQuote().getBudget()
            );

            DraftOrderResponse quote = recommendationService.draftOrder(request);
            OrderDraftSnapshot revised = buildDraftSnapshot(quote, ownerKey);

            replaceDraftAtomically(original, revised);
            return revised;
        }

        finally {
            guard.release(draftId);
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
