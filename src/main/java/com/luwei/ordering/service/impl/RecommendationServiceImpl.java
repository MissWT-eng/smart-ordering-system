package com.luwei.ordering.service.impl;

import com.luwei.ordering.common.enums.DishStatus;
import com.luwei.ordering.common.enums.UserStatus;
import com.luwei.ordering.common.exception.BusinessException;
import com.luwei.ordering.common.exception.ErrorCode;
import com.luwei.ordering.converter.DishConverter;
import com.luwei.ordering.dto.ai.DishReason;
import com.luwei.ordering.dto.ai.RecommendationResult;
import com.luwei.ordering.dto.request.RecommendationRequest;
import com.luwei.ordering.dto.response.*;
import com.luwei.ordering.entity.DishEntity;
import com.luwei.ordering.entity.RecommendationLogEntity;
import com.luwei.ordering.entity.UserEntity;
import com.luwei.ordering.mapper.DishMapper;
import com.luwei.ordering.mapper.RecommendationLogMapper;
import com.luwei.ordering.mapper.UserMapper;
import com.luwei.ordering.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationServiceImpl implements RecommendationService {
    //校验用户是否存在
    private final UserMapper userMapper;
    //检索菜品
    private final DishVectorService dishVectorService;
    //去数据库中反查dishes是否真实存在
    private final DishMapper dishMapper;
    //生成回答
    private final RecommendationAssistant recommendationAssistant;
    //落库
    private final RecommendationLogMapper recommendationLogMapper;
    //转化entity为DTO
    private final DishConverter dishConverter;
    //转List<Long> -> JSON,springBoot中自带一个@Bean的Jackson
    private final ObjectMapper objectMapper;
    //抽取用户preference，做粗筛
    private final QueryAnalyzer analyzer;
    //向量相似度权重
    @Value("${rerank.alpha:0.6}")
    private double alpha;
    //历史偏好权重
    @Value("${rerank.beta:0.4}")
    private double beta;
    //获取用户历史反馈
    private final FeedbackService feedbackService;


    @Override
    public RecommendationResponse recommendDishes(RecommendationRequest request) {
        //先做一个用户存在性校验
        UserEntity entity = userMapper.findById(request.getUserId());
        if(entity == null) throw new BusinessException(ErrorCode.NOT_EXIST);

        //再做一个用户状态检查,不为disabled就通过
        if(entity.getUserStatus() == UserStatus.DISABLED) throw new BusinessException(ErrorCode.USER_DISABLED);
        log.debug("当前使用AI推荐菜品的用户id为:{}", request.getUserId());


        //抽取用户preference，做粗筛
        QueryCondition condition;
        try{
            condition = analyzer.analyze(request.getPreference());
        }
        catch (Exception e){
            log.error("偏好分析失败,降级为无过滤条件:{}" , request.getPreference() , e);
            condition = null;
        }


        //调用dishVector的方法，向量化preference进行top5的检索
        List<DishMatch> dishMatches = dishVectorService.searchDishes(request.getPreference(), 5, condition);

        //如果带条件过滤检索出菜品为空,降级做无过滤
        if(dishMatches.isEmpty() && condition != null){
            log.warn("带条件过滤当前检索出菜品为空,降级做无过滤,preference:{}" , request.getPreference());
            dishMatches = dishVectorService.searchDishes(request.getPreference(), 5, null);
        }
        //取序号，按相似度降级取
        List<Long> dishNumbers = dishMatches.stream()
                                            .map(DishMatch::dishNumber)
                                            .toList();

        //再做一次校验，防止没有有通过条件的菜品
        if(dishNumbers.isEmpty()){
            return new RecommendationResponse(List.of(), "抱歉，暂时没有匹配到合适的菜品，换个口味描述试试？");
        }

        //保留向量分数
        Map<Long , Double> vectorScore = dishMatches.stream()
                                         .collect(Collectors.toMap(DishMatch :: dishNumber , DishMatch :: score));

        //反查+过滤，建一个[编号 -> 实体]的查询表
        Map<Long , DishEntity> availableMap = dishMapper.findByIds(dishNumbers).stream()
                                                    .filter(dish -> dish.getDishStatus() == DishStatus.AVAILABLE)
                                                    .collect(Collectors.toMap(DishEntity::getDishNumber , d -> d));

        //按照检索顺序从中逐个取回
        List<DishEntity> dishEntities = dishMatches.stream()
                                                    .map(m -> availableMap
                                                    .get(m.dishNumber()))
                                                    .filter(Objects::nonNull)
                                                    .toList();

        //这个检验主要是为了防止向量库中检索出已下架的菜品被推荐
        if(dishEntities.isEmpty()) return new RecommendationResponse(List.of(),"您喜欢的菜暂时都不在做，看看今日菜单里其他的？");

        //获取历史的 dishNumber -> affinity 硬反馈用来重排 (置信度 * 剧中)
        Map<Long, Double> affinity = feedbackService.getAffinityByDish(request.getUserId());

        //在这里做一个重排，重排一下dishEntities,线性加权，权重自己定
        dishEntities = dishEntities.stream()
                                   .sorted(Comparator.comparingDouble(
                                        (DishEntity d) -> alpha * vectorScore.getOrDefault(d.getDishNumber(), 0.0)
                                        + beta * affinity.getOrDefault(d.getDishNumber(), 0.0))
                        .reversed())
                        .toList();

        //取原始均分,传给LLM看(软反馈)
        Map<Long , Double> avgScoreByDish = feedbackService.getAvgScoreByDish(request.getUserId());

        //把实体类转化为String字符串
        String dishInfo =  dishEntities.stream()
                .map(d -> {
                  Double avg = avgScoreByDish.get(d.getDishNumber());
                  String line = String.format("菜品编号：%s，名称：%s，价格：%s",
                                              d.getDishNumber(),
                                              d.getDishName(),
                                              d.getPrice());
                  return avg == null ? line : line + String.format("，你曾给它评 %.1f 分" , avg);
                })
                .collect(Collectors.joining("\n"));

        //拼接到用户的输入中
        String userInput = "用户偏好:" + request.getPreference() + "\n 候选菜品: \n" + dishInfo;

        //调用LLM获取对应的建议或者理由
        RecommendationResult result;
        try{
            result = recommendationAssistant.recommend(userInput);
        }
        catch (Exception e){
            log.error("推荐理由生成失败,降级为无理由返回, userId:{}" , request.getUserId() , e);
            result = null;
        }
        // 编号 -> 理由 只保留候选集里真实的编号(丢弃幻觉)
        Map<Long , String> reasonMap = new HashMap<>();
        if(result != null && result.recommendations() != null){
            for(DishReason r : result.recommendations()){
                if(r.dishNumber() == null || r.reason() == null) continue;
                if(!availableMap.containsKey(r.dishNumber())){
                    log.warn("LLM 返回了候选集外的菜品编号, 已丢弃:{}" , r.dishNumber());
                    continue;
                }
                reasonMap.put(r.dishNumber() , r.reason());
            }
        }
        //主推编号检验 + 兜底
        Long primaryNumber = (result == null)? null : result.primaryDishNumber();
        if(primaryNumber != null && !availableMap.containsKey(primaryNumber)){
            log.warn("LLM 返回的主推编号不在候选集中 , 已忽略:{}" , primaryNumber);
            primaryNumber = null;
        }
        if(primaryNumber == null){
            //如果LLM挂了或者其他原因导致回答为空,返回一个相关度最高的回答
            primaryNumber = dishEntities.get(0).getDishNumber();
        }

        final Long primary = primaryNumber;
        //转entity为DTO，准备返回最后的结果
        List<RecommendedDishDTO> dishes = dishEntities.stream()
                .map(e -> new RecommendedDishDTO(
                        dishConverter.toListItemDTO(e),
                        reasonMap.get(e.getDishNumber()),
                        e.getDishNumber().equals(primary)))
                .toList();

        try {
            //把列表转为 -> [1,3,5]类似这种的
            List<Long> recommendedNumbers = dishEntities.stream()
                    .map(DishEntity::getDishNumber)
                    .toList();

            String recommendedDishes = objectMapper.writeValueAsString(recommendedNumbers);
            RecommendationLogEntity logEntity = new RecommendationLogEntity();
            logEntity.setUserId(request.getUserId());
            logEntity.setRecommendedDishes(recommendedDishes);

            recommendationLogMapper.insertLog(logEntity);
            log.info("推荐记录落库成功, userId:{}, 推荐菜品:{}", request.getUserId(), recommendedDishes);
        }

        catch(Exception e){
            log.error("推荐记录落库失败, userId:{}", request.getUserId(), e);
        }

        return new RecommendationResponse(dishes , result == null ? null : result.summary());
    }
}
