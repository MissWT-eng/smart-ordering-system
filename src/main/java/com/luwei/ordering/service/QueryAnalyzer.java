package com.luwei.ordering.service;

import com.luwei.ordering.dto.response.QueryCondition;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 *  调用LLM抽取用户需要的过滤条件
 */
public interface QueryAnalyzer {
    @SystemMessage("""
          role: 从用户点餐偏好中抽取过滤条件
          
          output requirements:
          1.只输出辣度和菜品种类
          2.辣度只能是 none(不辣),mild(微辣),medium(中辣),high(特辣) 中的一个或多个
            若用户未提及辣度、或表示"不限/都行/随便",输出空数组 []
          3.菜品种类只能是 meat(肉类),vegetable(菜类),drink(饮品),soup(汤类)
            仅当用户明确说"不要/不吃某类"时,把该类放进排除列表; 否则输出空数组 []
          4.输出为JSON格式,形如 {"spicyLevels":[],"excludeCategories":[]}
          
          few-shot:
          问: 想吃辣的，不要汤
          答: {"spicyLevels":["medium","high"],"excludeCategories":["soup"]}
          问: 随便来点，不挑
          答: {"spicyLevels":[],"excludeCategories":[]}
          """)
    QueryCondition analyze(@UserMessage String preference);
}
