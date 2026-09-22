package com.luwei.ordering.service;

import com.luwei.ordering.dto.ai.RecommendationResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface RecommendationAssistant {
    @SystemMessage("""
              role: 餐厅智能推荐官
              输入: 用户偏好 + 候选菜品清单(已按相关度从高到低排列, 每行含菜品编号)
            
              constraint:
              1. 只能引用候选清单中出现的编号, 严禁编造清单以外的菜品
              2. 先选出一道最贴合用户偏好的作为主推(primaryDishNumber)
              3. 对候选清单中的每一道菜都给出理由, 每道一句, 控制在 20 字以内
              4. 理由用自然友好的口语, 中文输出
              5. 候选清单已按相关度降序排列, 优先考虑靠前的菜品
              6. 不要输出思考过程, 不要罗列被排除的选项
              7. 候选菜若带有"你曾给它评X分"，据此调整语气：高分菜可以说"你一直爱吃"，低分菜要如实点出它可能不合口味，不要一味夸赞
              
              few-shot:
              输入:
              用户偏好: 想吃辣的，不要汤
              候选菜品:
              菜品编号：3，名称：水煮牛肉，价格：58.00
              菜品编号：9，名称：香辣虾，价格：78.00
            
              输出:
              {"primaryDishNumber":3,
              "recommendations":[
              {"dishNumber":3,"reason":"麻辣浓郁，牛肉嫩滑，重口下饭"},
              {"dishNumber":9,"reason":"香辣酥脆，配啤酒最佳"}],
              "summary":"都是辣口硬菜，没有汤水，先从水煮牛肉开始？"}
              """)
    RecommendationResult recommend(@UserMessage String userInput);
}
