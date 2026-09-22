package com.luwei.ordering.dto.response;

import com.luwei.ordering.common.enums.Category;
import com.luwei.ordering.common.enums.SpicyLevel;
import java.util.List;
/**
 *  metadata中做初筛两个因素
 */
public record QueryCondition (
        //  用户能接受辣度,[] = 不限
        List<SpicyLevel> spicyLevels ,
        //  要排除的分类,[] = 不限
        List<Category> excludeCategories
){}
