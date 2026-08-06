package com.xtu.homework.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类
 * 负责 Token 的生成、解析和校验
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret:MySuperSecretKeyForJWTTokenGeneration2026!@#$%^&*()}")
    private String secret;

    @Value("${jwt.expiration:7200000}")
    private long expiration; // 默认2小时 (毫秒)

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 JWT Token
     * @param userId   用户ID
     * @param username 用户名
     * @param role     角色
     * @return JWT Token 字符串
     */
    public String generateToken(Long userId, String username, String role) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expiration);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(exp)
                .signWith(getKey())
                .compact();
    }

    /**
     * 解析 Token 获取 Claims
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 验证 Token 是否有效
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /** 从 Token 获取用户ID */
    public Long getUserId(String token) {
        return Long.valueOf(parseToken(token).getSubject());
    }

    /** 从 Token 获取用户名 */
    public String getUsername(String token) {
        return parseToken(token).get("username", String.class);
    }

    /** 从 Token 获取角色 */
    public String getRole(String token) {
        return parseToken(token).get("role", String.class);
    }
}
