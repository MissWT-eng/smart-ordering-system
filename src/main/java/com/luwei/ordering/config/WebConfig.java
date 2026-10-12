package com.luwei.ordering.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final AdminInterceptor adminInterceptor;


    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*");
    }

    //采用白名单放行的策略
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 商户端：独立密钥，先注册
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/admin/**");

        // 用户端：默认全拦，显式放行公开接口（白名单）
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                //新增公开接口需要在这里记录
                .excludePathPatterns(
                        //页面与静态资源,否则前端页面直接打不开
                        "/", "/index.html", "/merchant_ui.html", "/user-frontend/**", "/error",
                        //登录（手机号识别，必须公开）
                        "/users/identify",
                        //公开业务接口
                        "/dishes/**",        // 用户端拉菜单
                        "/orders",           // 游客下单（POST 公开；GET /orders/{userId} 仍受保护）
                        "/recommendations",  // 游客可推荐
                        "/feedback",         // 当前公开
                        "/agent/chat",       // V2 Agent 入口（内部自己处理游客/注册）
                        //商户端交给 AdminInterceptor，避免被 auth 误拦
                        "/admin/**"
                );
    }


}
