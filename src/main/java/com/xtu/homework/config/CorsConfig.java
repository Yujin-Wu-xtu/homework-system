package com.xtu.homework.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import java.util.Arrays;

@Configuration
public class CorsConfig {
    /**
     * CORS 配置。本项目前端为同域单文件 SPA（static/ 静态资源 + /api 同源），正常使用无跨域请求；
     * 保留宽松来源仅为兼容分离部署场景。安全要点：
     * - JWT 放在 Authorization 头（无 Cookie），allowCredentials 无意义且会放大风险 → 不开启
     * - 任意来源 + 无凭据下，第三方网站无法读取用户 token（token 在 localStorage，跨域读不到）
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Arrays.asList("*"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("*"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
