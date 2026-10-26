package com.luwei.ordering.controller;

import com.luwei.ordering.common.exception.BusinessException;
import com.luwei.ordering.common.exception.ErrorCode;
import com.luwei.ordering.dto.request.ReviseDraftRequest;
import com.luwei.ordering.dto.response.ApiResponse;
import com.luwei.ordering.dto.response.OrderDraftSnapshot;
import com.luwei.ordering.dto.response.ReviseDraftResponse;
import com.luwei.ordering.service.OrderDraftService;
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
}
