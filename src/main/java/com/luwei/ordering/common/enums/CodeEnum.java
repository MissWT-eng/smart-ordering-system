package com.luwei.ordering.common.enums;

public interface CodeEnum {
    String getCode();

    static <E extends Enum<E> & CodeEnum> E fromCode(Class<E> type, String code) {
        String normalized = code == null ? null : code.trim();
        for (E e : type.getEnumConstants()) {
            if (e.getCode().equalsIgnoreCase(normalized)) return e;
        }
        throw new IllegalArgumentException("未知枚举值:" + code + ", 类型:" + type.getSimpleName());
    }

}
