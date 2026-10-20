package com.luwei.ordering.model.conversation;

import lombok.AllArgsConstructor;
import lombok.Data;
/**
 *  防止jvm重启后无法识别当前实例是否是之前实例
 */
@Data
@AllArgsConstructor
public class ConversationSession {
    private String ownerKey;
    private String instanceId;
}
