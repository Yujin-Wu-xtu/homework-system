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
        // SPA 路由（/login /teachers /myHomeworks 等，全部为单级无点路径）→ index.html
        // 注意：不能用 /** 或 ** 后接内容（Spring 6 PathPattern 限制），且会劫持 /vendor 等静态资源；
        // 带点路径（.js/.css/.png）由静态资源处理器服务，不受影响
        registry.addViewController("/{spring:[^\\\\.]*}")
                .setViewName("forward:/index.html");
    }
}
