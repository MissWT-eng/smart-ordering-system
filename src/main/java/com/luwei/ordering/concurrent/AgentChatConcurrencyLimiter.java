package com.luwei.ordering.concurrent;

import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;

@Component
public class AgentChatConcurrencyLimiter {
    private final Semaphore semaphore = new Semaphore(4);

    public boolean trySemaphore(){
        return semaphore.tryAcquire();
    }
    public void release(){
        semaphore.release();
    }

}
