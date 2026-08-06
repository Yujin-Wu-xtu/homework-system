package com.xtu.homework.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SPA 路由 fallback（Vue Router history 模式）
 * - 无点路径（如 /teachers、/myHomeworks）直达/刷新时转发到 index.html，交给前端路由渲染
 * - /api/** 由 Controller 精确映射优先匹配，不受影响；带点的静态资源（.js/.css/.png）由静态资源处理器服务
 */
@Configuration
public class SpaForwardConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/{spring:[^\\.]*}")
                .setViewName("forward:/index.html");
        registry.addViewController("/{spring:[^\\.]*}/**{path:[^\\.]*}")
                .setViewName("forward:/index.html");
    }
}
