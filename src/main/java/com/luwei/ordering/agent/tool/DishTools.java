package com.luwei.ordering.agent.tool;

import com.luwei.ordering.common.exception.BusinessException;
import com.luwei.ordering.common.exception.ErrorCode;
import com.luwei.ordering.dto.request.DraftOrderRequest;
import com.luwei.ordering.dto.request.RecommendationRequest;
import com.luwei.ordering.dto.response.*;
import com.luwei.ordering.service.DishService;
import com.luwei.ordering.service.OrderDraftService;
import com.luwei.ordering.service.RecommendationService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DishTools {
    private final DishService dishService;
    private final RecommendationService recommendationService;
    private final OrderDraftService orderDraftService;

    @Tool("查询菜单:返回当前所有在售菜品(名称、价格、分类、简介)，用于帮顾客点餐")
    public String queryMenu() {
        List<DishListItemDTO> dishes = dishService.listAvailableDishes();
        if (dishes.isEmpty()) {
            return "当前暂无可点菜品。";
        }
        StringBuilder sb = new StringBuilder();
        for (DishListItemDTO d : dishes) {
            sb.append("- ").append(d.getDishName())
                           .append("(")
                           .append("菜品编号:")
                           .append(d.getDishNumber())
                           .append(d.getCategory())
                           .append(",RMB:").append(d.getPrice()).append("):")
                           .append(d.getDishIntroduction()).append("\n");
        }
        return sb.toString();
    }

    @Tool("推荐菜品:根据顾客口味偏好、人数、预算调用后端推荐引擎，给出菜品与理由")
    public RecommendationToolResult recommendDish(
            @P(value = "顾客口味/场景偏好，如『清淡下饭』『来点辣的』", required = true) String preference,
            @P(value = "用餐人数", required = false) Integer peopleNum,
            @P(value = "预算上限（元）", required = false) BigDecimal budget) {

        // 兜底：preference 是唯一的必填,空了直接给第一层的LLM返回结果
        if (preference == null || preference.isBlank()) {

            return new RecommendationToolResult(
                    RecommendationToolResult.Status.INVALID_ARGUMENTS,
                    List.of(),
                    "偏好不能为空",
                    "重新询问顾客: 想吃什么口味或场景（比如『清淡下饭』『下酒菜』）");

        }

        RecommendationRequest req = new RecommendationRequest();
        req.setPreference(preference);
        req.setPeopleNum(peopleNum);
        req.setBudget(budget);            // userId 留空 = 游客，token 传 null
        RecommendationResponse resp;

        try {
            resp = recommendationService.recommendDishes(req, null);
        }
        catch (BusinessException e) {
            if (e.getErrorCode() != ErrorCode.PARAM_ERROR) {
                throw e;
            }
            return new RecommendationToolResult(
                    RecommendationToolResult.Status.INVALID_ARGUMENTS,
                    List.of(),
                    "推荐参数不合法：" + e.getMessage(),
                     "请依据顾客原话修正参数；"
                            + "信息不明确时先询问顾客，不要擅自放宽预算。"
            );
        }


        List<RecommendedDishDTO> dishes = resp.getDishes();
        if (dishes == null || dishes.isEmpty()) {
            return new RecommendationToolResult(
                    RecommendationToolResult.Status.NO_MATCH,
                    List.of(),
                    resp.getSummary() != null
                            ? resp.getSummary()
                            :"暂时没有匹配到合适的菜品",
                    "告知顾客，并询问是否调整需求；不自行放宽约束"
            );

        }

        return new RecommendationToolResult(
                RecommendationToolResult.Status.SUCCESS,
                resp.getDishes(),
                resp.getSummary(),
                "展示候选菜品及单价，询问顾客选择和数量；尚未创建订单。"
        );
    }

    @Tool("试算并保存临时订单草稿，返回编号和报价，尚未创建正式订单")
    public DraftOrderToolResult draftOrder(@P(value = "待试算的菜品编号、份数及可选预算",required = true)
                                           DraftOrderRequest req,
                                           InvocationParameters parameters)
    {
        String ownerKey = parameters.get("ownerKey");
        DraftOrderResponse  response = null;
        OrderDraftSnapshot snapshot = null;

        if(req == null){
            return new DraftOrderToolResult(
                    null,
                    null,
                    DraftOrderToolResult.Status.INVALID_ARGUMENTS,
                    null,
                    "请求参数不能为空",
                    "重新检查参数问题");
        }

        try {
            snapshot = orderDraftService.createDraft(req, ownerKey);
            response = snapshot.getQuote();
        }
        catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.PARAM_ERROR) {
                return new DraftOrderToolResult(
                        null,
                        null,
                        DraftOrderToolResult.Status.INVALID_ARGUMENTS,
                        null,
                        "推荐参数不合法：" + e.getMessage(),
                        "请依据顾客原话修正参数；"
                                + "信息不明确时先询问顾客。"
                );
            }

            if(e.getErrorCode() == ErrorCode.DISH_NOT_AVAILABLE){
                return new DraftOrderToolResult(
                        null,
                        null,
                        DraftOrderToolResult.Status.DISH_UNAVAILABLE,
                        null,
                        "菜品已经下架或处于未售状态：" + e.getMessage(),
                        "告知用户当前菜品已经下架或处于未售状态"
                );
            }
            throw e;
        }

        if(! Boolean.TRUE.equals(response.getOverBudget())){
            return new DraftOrderToolResult(
                    snapshot.getDraftId(),
                    snapshot.getExpireAt(),
                    DraftOrderToolResult.Status.SUCCESS,response,
                    "试算完成，尚未创建订单",
                    "询问用户当前模型选择出的菜品是否妥当,当前菜品未超出预算,尚未创建订单"
                    );
        }
        else {
            return new DraftOrderToolResult(
                    snapshot.getDraftId(),
                    snapshot.getExpireAt(),
                    DraftOrderToolResult.Status.BUDGET_EXCEEDED,response,
                    "试算完成，总金额超过预算",
                    "询问用户当前模型选择出的菜品是否妥当,告知超预算，询问如何调整菜品或数量,尚未创建订单"
            );
        }
    }

    @Tool("用户确认订单后，用于正式下单")
    public ConfirmOrderToolResult confirmOrder(@P(value = "待确认的草稿编号", required = true) String draftId,
                                               InvocationParameters parameters) {
        if (parameters == null || draftId == null || draftId.isBlank()) {
            return new ConfirmOrderToolResult(
                    ConfirmOrderToolResult.Status.INVALID_ARGUMENTS,
                    null,
                    "当前必要参数为空",
                    "当前参数不合法，停止下单");
        }

        String ownerKey = parameters.get("ownerKey");
        String confirmedDraftId = parameters.get("confirmedDraftId");

        if (confirmedDraftId == null || confirmedDraftId.isBlank()) {
            return new ConfirmOrderToolResult(
                    ConfirmOrderToolResult.Status.CONFIRMATION_REQUIRED,
                    null,
                    "当前草稿未被用户确认",
                    "需要用户明确确认,停止下单");
        }

        if (ownerKey == null || ownerKey.isBlank()) {
            return new ConfirmOrderToolResult(
                    ConfirmOrderToolResult.Status.INVALID_ARGUMENTS,
                    null,
                    "缺少调用身份",
                    "停止下单"
            );
        }

        if (!draftId.equals(confirmedDraftId)) {
            return new ConfirmOrderToolResult(
                    ConfirmOrderToolResult.Status.CONFIRMATION_MISMATCH,
                    null,
                    "当前订单草稿号匹配失败",
                    "该草稿未经本次确认");
        }
        OrderListItemDTO order = null;
        try {
            order = orderDraftService.confirmDraft(draftId, ownerKey);
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.DRAFT_DISH_INFO_ERROR) {
                return new ConfirmOrderToolResult(
                        ConfirmOrderToolResult.Status.INVALID_ARGUMENTS,
                        null,
                        "菜品价格发生变化",
                        "当前菜品价格发生变化，询问并引导用户重新选择下单"
                );

            }

            if (e.getErrorCode() == ErrorCode.DRAFT_NOT_FOUND_OR_EXPIRED) {
                return new ConfirmOrderToolResult(
                        ConfirmOrderToolResult.Status.INVALID_ARGUMENTS,
                        null,
                        "草稿不存在或已过期",
                        "重新试算生成草稿，展示报价，并请用户再次明确确认"
                );
            }

            if (e.getErrorCode() == ErrorCode.BUDGET_EXCEEDED) {
                return new ConfirmOrderToolResult(
                        ConfirmOrderToolResult.Status.BUDGET_EXCEEDED,
                        null,
                        "当前草稿菜品已经超出预算",
                        "调整菜品或数量，重新试算并确认"
                );
            }

            if (e.getErrorCode() == ErrorCode.DISH_NOT_AVAILABLE) {
                return new ConfirmOrderToolResult(
                        ConfirmOrderToolResult.Status.DISH_NOT_AVAILABLE,
                        null,
                        "草稿菜品已经下架",
                        "更换菜品，重新试算并确认"
                );
            }

            if (e.getErrorCode() == ErrorCode.FORBIDDEN) {
                return new ConfirmOrderToolResult(
                        ConfirmOrderToolResult.Status.FORBIDDEN,
                        null,
                        "凭证与身份不符",
                        "告知无法操作该草稿，停止下单"
                );

            }
            throw e;
        }
        return new ConfirmOrderToolResult(
                ConfirmOrderToolResult.Status.SUCCESS,
                order,
                "已返回该草稿对应的订单",
                "依据返回订单的实际编号、状态、金额和明细回复用户");
    }
    
}
