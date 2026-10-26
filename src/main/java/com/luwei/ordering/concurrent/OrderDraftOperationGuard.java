package com.luwei.ordering.concurrent;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OrderDraftOperationGuard {
    private final Set<String> processingDrafts = ConcurrentHashMap.newKeySet();

    public boolean tryAcquire(String draftId){
        return processingDrafts.add(draftId);
    }

    public void release(String draftId){
        processingDrafts.remove(draftId);
    }

}
