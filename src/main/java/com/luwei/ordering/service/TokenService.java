package com.luwei.ordering.service;

public interface TokenService {
    /**
     *  签发:为用户生成token(带过期时间) 返回token字符串
     */
    String createToken(Long userId);

    /**
     *  检验:如果token存在并未过期 -> 返回用户id；token不存在或者过期 -> 返回null
     */
    Long resolve(String token);
}
