package com.luwei.ordering.service.impl;

import com.luwei.ordering.common.enums.DishStatus;
import com.luwei.ordering.common.exception.BusinessException;
import com.luwei.ordering.common.exception.ErrorCode;
import com.luwei.ordering.converter.DishConverter;
import com.luwei.ordering.dto.request.DishAddRequest;
import com.luwei.ordering.dto.request.DishQueryRequest;
import com.luwei.ordering.dto.request.DishStatusUpdateRequest;
import com.luwei.ordering.dto.request.DishUpdateRequest;
import com.luwei.ordering.dto.response.DishListItemDTO;
import com.luwei.ordering.dto.response.PageResult;
import com.luwei.ordering.entity.DishEntity;
import com.luwei.ordering.mapper.DishMapper;
import com.luwei.ordering.service.DishService;
import com.luwei.ordering.service.DishVectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DishServiceImpl implements DishService {
    private final DishMapper dishMapper;
    private final DishConverter dishConverter;
    private final DishVectorService dishVectorService;

    @Override
    @Transactional(readOnly = true)
    public PageResult<DishListItemDTO> queryDishes(DishQueryRequest request) {
        log.debug("查询菜品, 入参: {}", request);
        long total = dishMapper.countDishes(request);
        long offset = (long) (request.getPageNum() - 1) * request.getPageSize();

        List<DishEntity> dishes = dishMapper.queryDishesByPage(request, offset);
        int totalPages = (int) ((total + request.getPageSize() - 1) / request.getPageSize());
        List<DishListItemDTO> list = dishes.stream()
                                    .map(dishConverter :: toListItemDTO)
                                    .toList();
        return new PageResult<>(total, request.getPageNum(), request.getPageSize(), list, totalPages);
    }

    @Override
    @Transactional
    public Long addDish(DishAddRequest request) {
        //做菜品名不重复检查
        if (dishMapper.countByName(request.getDishName()) > 0) {
            throw new BusinessException(ErrorCode.DISH_NAME_EXISTS);
        }

        //转化DishAddRequest 为 DishEntity
        DishEntity entity = dishConverter.toEntity(request);
        entity.setDishStatus(DishStatus.AVAILABLE);
        dishMapper.insertDish(entity);

        //向量库同步操作，这里旁路降级形态
        try {
            dishVectorService.syncDish(entity);
        }
        catch(Exception e)
        {
            log.error("向量库同步失败,dishNumber:{}" , entity.getDishNumber() ,e);
        }

        log.info("新增菜品成功, dishNumber={}, dishName={}" ,
                 entity.getDishNumber() ,entity.getDishName());

        return entity.getDishNumber();
    }

    @Override
    @Transactional
    public void updateDish(Long dishNumber, DishUpdateRequest request) {
        //先做存在性校验
        DishEntity entity = dishMapper.findById(dishNumber);
        if(entity == null)
        {
            throw new BusinessException(ErrorCode.NOT_EXIST);
        }
        //再做一个同菜品名检查(排除自己)
        if(dishMapper.countByNameExcludeId(dishNumber , request.getDishName()) > 0 )
        {
            throw new BusinessException(ErrorCode.DISH_NAME_EXISTS);
        }
        //先把dishUpdateRequest转化为DishEntity
        dishConverter.updateEntity(request , entity);

        //确认完成后，更新菜品
        dishMapper.updateDish(entity);

        //向量库同步操作，这里旁路降级形态
        try {
            dishVectorService.syncDish(entity);
        }
        catch(Exception e)
        {
            log.error("向量库同步失败,dishNumber:{}" , entity.getDishNumber() ,e);
        }

        log.info("修改菜品成功, dishNumber:{}" , dishNumber);
    }

    @Override
    @Transactional
    public void updateDishStatus(Long dishNumber, DishStatusUpdateRequest request) {
        //先做存在性校验
        DishEntity entity = dishMapper.findById(dishNumber);
        if(entity == null)
        {
            throw new BusinessException(ErrorCode.NOT_EXIST);
        }
        //确认完成后，更新菜品上下架状态
        dishMapper.updateStatus(request.getStatus() , dishNumber);

        //把内存的对象状态和数据库同步一下，防止后续的向量化出错(对齐内存)
        entity.setDishStatus(request.getStatus());

        //向量库同步操作，这里旁路降级形态
        try {
            if(entity.getDishStatus() == DishStatus.AVAILABLE) {
                dishVectorService.syncDish(entity);
            }
            else{
                dishVectorService.removeDish(entity.getDishNumber());
            }
        }
        catch(Exception e)
        {
            log.error("向量库同步失败,dishNumber:{}" , entity.getDishNumber() ,e);
        }

        log.info("修改菜品状态成功 , dishNumber:{}" , dishNumber);
    }

    @Override
    @Transactional(readOnly = true)
    public DishListItemDTO getDish(Long dishNumber) {
        DishEntity entity = dishMapper.findById(dishNumber);
        if(entity == null){
            throw new BusinessException(ErrorCode.NOT_EXIST);
        }
        return dishConverter.toListItemDTO(entity);
    }
}
