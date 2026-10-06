package com.luwei.ordering.controller;

import com.luwei.ordering.dto.request.*;
import com.luwei.ordering.dto.response.ApiResponse;
import com.luwei.ordering.dto.response.DishListItemDTO;
import com.luwei.ordering.dto.response.PageResult;
import com.luwei.ordering.service.DishService;
import com.luwei.ordering.service.DishVectorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


/**
 * 菜品单模块接口
 */
@RestController
@RequestMapping("/dishes")
@RequiredArgsConstructor

public class DishController {

    private final DishService dishService;
    /**
     * 查询全部菜品
     */
    @GetMapping
    public ApiResponse<PageResult<DishListItemDTO>> queryDishes(@Valid DishQueryRequest request){
        return ApiResponse.success(dishService.queryDishes(request));
    }
    /**
     *  查询单个菜品
     */
    @GetMapping("/{dishNumber}")
    public ApiResponse<DishListItemDTO> getDish(@PathVariable Long dishNumber){
        return ApiResponse.success(dishService.getDish(dishNumber));
    }


}
