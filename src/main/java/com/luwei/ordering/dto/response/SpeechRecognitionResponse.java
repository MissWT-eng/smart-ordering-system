package com.luwei.ordering.dto.response;

//  阿里云百炼识别用户语言的record类
//  text 就是云端识别出的文字，例如“我们两个人，预算一百元”。
public record SpeechRecognitionResponse(String text) {}
