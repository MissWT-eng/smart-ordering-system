package com.luwei.ordering.agent.tool;

import com.luwei.ordering.common.exception.BusinessException;
import com.luwei.ordering.common.exception.ErrorCode;
import com.luwei.ordering.dto.response.OrderHistoryToolResult;
import com.luwei.ordering.dto.response.OrderListItemDTO;
import com.luwei.ordering.dto.response.PageResult;
import com.luwei.ordering.service.OrderService;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderTools {

    private final OrderService orderService;

    @Tool("查询顾客本人的历史订单，固定返回最近 5 条；仅限当前登录顾客，游客不可用")
    public OrderHistoryToolResult queryOrderHistory(InvocationParameters parameters) {
        Long userId = parameters.get("userId");

        if (userId == null) {
            return new OrderHistoryToolResult(
                    OrderHistoryToolResult.Status.LOGIN_REQUIRED,
                    List.of(),
                    0L,
                    "缺少登录身份",
                    "提示顾客先登录，不要编造订单");
        }

        try {
            PageResult<OrderListItemDTO> page = orderService.queryOrders(userId, 1, 5);

            if (page == null || page.getList() == null || page.getList().isEmpty()) {
                return new OrderHistoryToolResult(
                        OrderHistoryToolResult.Status.NO_ORDERS,
                        List.of(),
                        0L,
                        "该顾客暂无历史订单",
                        "如实告知暂无历史订单，可引导订餐，不要说系统异常");
            }

            return new OrderHistoryToolResult(
                    OrderHistoryToolResult.Status.SUCCESS,
                    page.getList(),
                    page.getTotal(),
                    "共 " + page.getTotal() + " 单，本次返回最近 " + page.getList().size() + " 单",
                    "按时间倒序说明菜品、数量、金额和状态；"
                            + "PENDING=待处理，PAID=已支付，CANCELLED=已取消；只陈述返回的订单");
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.USER_DISABLED) {
                return new OrderHistoryToolResult(
                        OrderHistoryToolResult.Status.LOGIN_REQUIRED,
                        List.of(),
                        0L,
                        "该顾客账号已禁用",
                        "提示顾客账号已禁用，不要编造订单");
            }

            if (e.getErrorCode() == ErrorCode.NOT_EXIST) {
                return new OrderHistoryToolResult(
                        OrderHistoryToolResult.Status.LOGIN_REQUIRED,
                        List.of(),
                        0L,
                        "系统未录入该顾客",
                        "提示顾客账号不存在，需要重新登录或联系门店，不要编造订单");
            }

            throw e;
        }
    }
}
