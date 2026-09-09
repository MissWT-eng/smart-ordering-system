package com.luwei.ordering.service;

import com.luwei.ordering.dto.request.DishAddRequest;
import com.luwei.ordering.dto.request.DishQueryRequest;
import com.luwei.ordering.dto.request.DishStatusUpdateRequest;
import com.luwei.ordering.dto.request.DishUpdateRequest;
import com.luwei.ordering.dto.response.DishListItemDTO;
import com.luwei.ordering.dto.response.PageResult;


public interface DishService {
    PageResult<DishListItemDTO> queryDishes(DishQueryRequest request);
    Long addDish(DishAddRequest request);
    void updateDish(Long dishNumber ,DishUpdateRequest request);
    void updateDishStatus(Long dishNumber , DishStatusUpdateRequest request);
    DishListItemDTO getDish(Long dishNumber);
}
