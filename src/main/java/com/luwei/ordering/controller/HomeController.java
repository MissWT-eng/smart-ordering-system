package com.luwei.ordering.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 根路径重定向到实际的前端入口。
 * 前端静态资源放在 static/user-frontend/ 下，Spring Boot 只会把 static/index.html 映射到 /，
 * 子目录里的需要显式跳转，否则访问 IP 根路径会 500。
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "redirect:/user-frontend/index.html";
    }
}
