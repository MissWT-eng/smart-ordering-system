package com.luwei.ordering.controller;

import com.luwei.ordering.common.enums.LookupStatus;
import com.luwei.ordering.common.exception.BusinessException;
import com.luwei.ordering.common.exception.ErrorCode;
import com.luwei.ordering.dto.request.ReviseDraftRequest;
import com.luwei.ordering.dto.response.*;
import com.luwei.ordering.service.OrderDraftService;
import com.luwei.ordering.service.OrderService;
import com.luwei.ordering.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/order-drafts")
@AllArgsConstructor

public class OrderDraftController {
    private final TokenService tokenService;
    private final OrderDraftService orderDraftService;
    private final OrderService orderService;

    @PostMapping("/{draftId}/revisions")
    public ApiResponse<ReviseDraftResponse> reviseDraft(@PathVariable String draftId,
                                                        @Valid @RequestBody ReviseDraftRequest req,
                                                       HttpServletRequest httpServletRequest){
        String token = httpServletRequest.getHeader("X-User-Token");
        String ownerKey;
        Long userId;

        if(token == null){
            ownerKey = "guest" + httpServletRequest.getSession().getId();
        }

        else {
            userId = tokenService.resolve(token);

            if (userId == null) {
                throw new BusinessException(ErrorCode.NOT_AUTH);
            }
            ownerKey = "user:" + userId;
        }

        OrderDraftSnapshot revised = orderDraftService.reviseDraft(draftId, req, ownerKey);
         return ApiResponse.success(
                 new ReviseDraftResponse(
                         revised.getDraftId(),
                         revised.getExpireAt(),
                         revised.getQuote()
                 ));
    }

    @GetMapping("/{draftId}/order")
    public ApiResponse<OrderLookupResponse> lookupOrder(@PathVariable String draftId,
                                                        HttpServletRequest httpServletRequest){
        String token = httpServletRequest.getHeader("X-User-Token");
        String ownerKey;
        Long userId;

        if(token == null){
            ownerKey = "guest" + httpServletRequest.getSession().getId();
        }

        else {
            userId = tokenService.resolve(token);

            if (userId == null) {
                throw new BusinessException(ErrorCode.NOT_AUTH);
            }
            ownerKey = "user:" + userId;
        }

        OrderListItemDTO order = orderService.findOrderByDraftId(draftId, ownerKey);
        if(order == null){
            return ApiResponse.success(
                    new OrderLookupResponse(
                            LookupStatus.NOT_FOUND,
                            null,
                            "暂未查询到订单，提交可能仍在处理中，请稍后核实"
                    )
            );
        }
        return ApiResponse.success(
                new OrderLookupResponse(
                        LookupStatus.FOUND,
                        order,
                        "已查询到订单"
                )
        );
    }
}
