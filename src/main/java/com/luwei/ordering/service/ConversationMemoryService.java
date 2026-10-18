package com.luwei.ordering.service;

public interface ConversationMemoryService {
    void clearConversationMemory(String conversationId);

    void cleanupExpiredConversations();

    //登记这个编号，并把请求数加一。
    void beginUse(String conversationId);
    //把请求数减一，减到零时保留记录。
    void endUse(String conversationId);
}
