package com.xtu.homework.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

/**
 * SPA 路由 fallback（Vue Router history 模式）
 * - 无点路径（如 /teachers、/myHomeworks）直达/刷新时转发到 index.html，交给前端路由渲染
 * - /api/** 由 Controller 精确映射优先匹配，不受影响；带点的静态资源（.js/.css/.png）由静态资源处理器服务
 * - /question-images/** 映射到磁盘 data/question-images/（题干富文本图片，Windows/WSL/Docker 三端按 user.dir 解析）
 */
@Configuration
public class SpaForwardConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // SPA 路由（/login /teachers /myHomeworks 等，全部为单级无点路径）→ index.html
        // 注意：不能用 /** 或 ** 后接内容（Spring 6 PathPattern 限制），且会劫持 /vendor 等静态资源；
        // 带点路径（.js/.css/.png）由静态资源处理器服务，不受影响
        // 正则 [^\\.]* 表示"不含点号"（Spring PathPattern 自己解析反斜杠转义，源码里需 \\\\. 即 4 个反斜杠）
        registry.addViewController("/{spring:[^\\\\.]*}")
                .setViewName("forward:/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 题干图片（应用题富文本插图）：<img src="/question-images/xxx.png">
        String imgDir = System.getProperty("user.dir") + File.separator
                + "data" + File.separator + "question-images" + File.separator;
        registry.addResourceHandler("/question-images/**")
                .addResourceLocations("file:" + imgDir);
    }
}
