package com.luwei.ordering.dto.response;

import com.luwei.ordering.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ApiResponse<T> {
    private int code;
    private String msg;
    private T data;

    public static <T> ApiResponse<T> success(T data){
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMsg() , data);
    }

    /**
     *定义一个无参数的success方法，用来返回更新成功、修改成功等结果
     */
    public static <T> ApiResponse<T> success(){
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode() , ErrorCode.SUCCESS.getMsg() , null);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode){
        return new ApiResponse<>(errorCode.getCode() , errorCode.getMsg() , null);
    }


    /**
     * 自定义错误信息，重载error方法
     */
    public static <T> ApiResponse<T> error(ErrorCode errorCode , String message){
        return new ApiResponse<>(errorCode.getCode() , message , null);
    }

}
