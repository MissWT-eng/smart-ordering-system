package com.luwei.ordering.common.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    SUCCESS(0 , "success"),
    PARAM_ERROR(400 ,"参数错误"),
    CONVERSATION_BUSY(409,"当前对话正在回复中"),
    CONVERSATION_CONTEXT_LOST(409,"对话上下文已失效，请开启新对话"),
    DRAFT_NOT_FOUND_OR_EXPIRED(404, "草稿不存在或已过期"),
    CONVERSATION_NOT_FOUND_OR_EXPIRED(404, "对话不存在或已过期"),
    BUDGET_EXCEEDED(400, "超出预算"),
    DRAFT_DISH_INFO_ERROR(400, "菜品价格发生变化"),
    NOT_EXIST(404 , "访问资源不存在"),
    SYSTEM_ERROR(500 , "系统内部错误"),
    PREFERENCE_ANALYSIS_FAILED(503, "暂时无法解析推荐条件，请稍后重试"),
    SPEECH_SERVICE_UNAVAILABLE(503, "语音识别服务暂不可用"),
    UPLOAD_TOO_LARGE(413,"上传文件或请求体过大"),
    UNSUPPORTED_MEDIA_TYPE(415, "请求内容类型不受支持"),
    DISH_NAME_EXISTS(409 , "菜品名已存在"),
    DRAFT_ALREADY_CONFIRMED(409,"订单已确认"),
    DRAFT_REPLACED(409, "该报价已被新报价替换，请核对最新报价"),
    DRAFT_BUSY(409, "该报价正在处理中，请稍后重试"),
    USER_DISABLED(403 , "该用户已被禁用"),
    DISH_NOT_AVAILABLE(409 , "该菜品不存在或已下架"),
    ILLEGAL_QUANTITY(400, "菜品数量不合法"),
    CANT_TRANSITION_TO(409,"订单状态不允许改变"),
    NOT_AUTH(401, "未授权用户"),
    LLM_SERVICE_UNAVAILABLE(504, "当前调用超时"),
    AGENT_BUSY(503,"当前聊天请求过多，请稍后重试"),
    SPEECH_SYNTHESIS_UNAVAILABLE(503, "语音合成服务暂不可用"),
    WEATHER_SERVICE_UNAVAILABLE(504, "当前调用异常"),
    CONVERSATION_CAPACITY_EXCEEDED(503, "当前会话较多，请稍后再试"),
    FORBIDDEN(403, "凭证与身份不符");
    private final int code;
    private final String msg;

    ErrorCode(int code, String msg){
        this.msg =msg;
        this.code = code;
    }
}
