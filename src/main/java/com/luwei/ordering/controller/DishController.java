package com.luwei.ordering.controller;

import com.luwei.ordering.dto.request.DishAddRequest;
import com.luwei.ordering.dto.request.DishQueryRequest;
import com.luwei.ordering.dto.request.DishStatusUpdateRequest;
import com.luwei.ordering.dto.request.DishUpdateRequest;
import com.luwei.ordering.dto.response.ApiResponse;
import com.luwei.ordering.dto.response.DishListItemDTO;
import com.luwei.ordering.dto.response.PageResult;
import com.luwei.ordering.service.DishService;
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



    /**
     * 添加新菜品
     */
    @PostMapping
    public ApiResponse<Long> addDish(@Valid @RequestBody DishAddRequest request){
        return ApiResponse.success(dishService.addDish(request));
    }
    /**
     * 更新菜品信息
     */
    @PutMapping("/{dishNumber}")
    public ApiResponse<Void> updateDish(@PathVariable Long dishNumber ,
                                        @Valid @RequestBody DishUpdateRequest request)
    {
        dishService.updateDish(dishNumber , request);
        return ApiResponse.success();
    }
    /**
     *  上下架对应菜品
     */
    @PutMapping("/{dishNumber}/status")
    public ApiResponse<Void> updateDishStatus(@PathVariable Long dishNumber ,
                                              @Valid @RequestBody DishStatusUpdateRequest request)
    {
        dishService.updateDishStatus(dishNumber , request);
        return ApiResponse.success();
    }
}
