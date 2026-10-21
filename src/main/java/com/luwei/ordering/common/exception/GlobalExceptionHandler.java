package com.luwei.ordering.common.exception;

import com.luwei.ordering.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;


@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e){
        log.warn("业务异常: code ={} , msg = {}" , e.getErrorCode().getCode(), e.getErrorCode().getMsg());
        return  ResponseEntity
                .status(e.getErrorCode().getCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(e.getErrorCode() , e.getMessage() ));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(BindException e){
        String message = e.getFieldError() != null ? e.getFieldError().getDefaultMessage() : ErrorCode.PARAM_ERROR.getMsg();
        log.warn("参数校验失败:{}" , message);
        return ResponseEntity.status(ErrorCode.PARAM_ERROR.getCode())
                             .contentType(MediaType.APPLICATION_JSON)
                             .body(ApiResponse.error(ErrorCode.PARAM_ERROR , message));
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e){
        log.error("系统异常" , e);
        return  ResponseEntity
                .status(ErrorCode.SYSTEM_ERROR.getCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(ErrorCode.SYSTEM_ERROR));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException e){
        log.warn("请求体解析失败: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.PARAM_ERROR.getCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(ErrorCode.PARAM_ERROR, "请求体格式错误或参数非法"));
    }


    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e){
        log.warn("参数类型错误: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.PARAM_ERROR.getCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(ErrorCode.PARAM_ERROR, "参数类型错误"));
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleDisconnectedStream(AsyncRequestNotUsableException e) {
        // 已断开的异步响应无法再写入 JSON 错误体。
        log.debug("异步响应连接已不可用", e);
    }

}
