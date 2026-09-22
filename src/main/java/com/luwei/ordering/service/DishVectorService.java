package com.luwei.ordering.service;

import com.luwei.ordering.dto.response.DishMatch;
import com.luwei.ordering.dto.response.QueryCondition;
import com.luwei.ordering.entity.DishEntity;
import java.util.List;


public interface DishVectorService {
    void syncDish(DishEntity entity);
    void removeDish(Long dishNumber);
    void resyncDish();
    List<DishMatch> searchDishes(String query , int topK, QueryCondition condition);
}
