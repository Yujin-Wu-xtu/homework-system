package com.xtu.homework.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xtu.homework.HomeworkApplication;
import com.xtu.homework.util.JwtUtil;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;

/**
 * Controller 集成测试基类（MockMvc 全链路：JWT 过滤器 + Security + Controller + Service + H2 库）。
 *
 * 设计约定：
 * - token 直接用 JwtUtil 生成（不依赖初始密码）——UserServiceImplTest.testResetPassword(Order 5)
 *   会把 admin 密码重置为随机值，测试类间执行顺序不确定，真实登录只在本基类外的
 *   AuthControllerTest 用自建账号验证。
 * - 独立 H2 库（mem:ctest）：controller 测试与 service 测试的 Spring 上下文配置不同
 *   （@AutoConfigureMockMvc），若共用 mem:test 库，第二个上下文初始化时 data-h2.sql
 *   重复插入 admin 会违反唯一约束（DB_CLOSE_DELAY=-1 保持库到 JVM 退出）。
 * - 响应断言：业务响应统一 R 格式（HTTP 恒 200 + body.code）；鉴权异常（401/403）由
 *   Spring Security 层产生真实 HTTP 状态码。
 * - 初始数据（H2 data-h2.sql 硬编码 ID）：admin=1 / 张老师 T2024001=2 / 张三 20240001=3，
 *   教学班 id=1（2024级数据结构教学班，teacher_id=2，关联自然班 1、2）。
 */
@SpringBootTest(classes = HomeworkApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:ctest;MODE=MySQL;DB_CLOSE_DELAY=-1")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class BaseControllerTest {

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected JwtUtil jwtUtil;
    @Autowired
    protected ObjectMapper objectMapper;

    protected static final Long ADMIN_ID = 1L;
    protected static final Long TEACHER_ID = 2L;
    protected static final Long STUDENT_ID = 3L;   // 20240001 张三（自然班 1）
    protected static final Long STUDENT4_ID = 6L;  // 20240004 赵六（自然班 2）
    protected static final String ADMIN_USERNAME = "admin";
    protected static final String TEACHER_USERNAME = "T2024001";
    protected static final String STUDENT_USERNAME = "20240001";
    protected static final Long TEACHING_CLASS_1 = 1L;   // 初始教学班（关联自然班 1、2）
    protected static final Long CLAZZ_1 = 1L;
    protected static final Long CLAZZ_2 = 2L;
    protected static final Long QUESTION_1 = 1L;         // 单选 C
    protected static final Long QUESTION_5 = 5L;         // 问答题

    // ---------- token 工具 ----------

    protected String adminToken() { return tokenOf(ADMIN_ID, ADMIN_USERNAME, "ADMIN"); }
    protected String teacherToken() { return tokenOf(TEACHER_ID, TEACHER_USERNAME, "TEACHER"); }
    protected String studentToken() { return tokenOf(STUDENT_ID, STUDENT_USERNAME, "STUDENT"); }
    protected String student4Token() { return tokenOf(STUDENT4_ID, "20240004", "STUDENT"); }
    protected String tokenOf(Long userId, String username, String role) {
        return jwtUtil.generateToken(userId, username, role);
    }

    // ---------- 请求封装 ----------

    protected MvcResult perform(MockHttpServletRequestBuilder builder, String token) throws Exception {
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return mockMvc.perform(builder).andReturn();
    }

    protected MvcResult get(String url, String token) throws Exception {
        return perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url), token);
    }

    protected MvcResult postJson(String url, Object body, String token) throws Exception {
        return perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)), token);
    }

    protected MvcResult putJson(String url, Object body, String token) throws Exception {
        return perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)), token);
    }

    protected MvcResult delete(String url, String token) throws Exception {
        return perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(url), token);
    }

    // ---------- 响应解析 ----------

    protected JsonNode body(MvcResult r) throws Exception {
        return objectMapper.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    protected int code(MvcResult r) throws Exception {
        return body(r).path("code").asInt();
    }

    protected String msg(MvcResult r) throws Exception {
        return body(r).path("msg").asText();
    }

    protected int httpStatus(MvcResult r) {
        return r.getResponse().getStatus();
    }

    /** 业务成功断言：HTTP 200 + body.code == 200 */
    protected void assertOk(MvcResult r) throws Exception {
        assertOk(r, "请求应成功");
    }

    protected void assertOk(MvcResult r, String message) throws Exception {
        org.junit.jupiter.api.Assertions.assertEquals(200, httpStatus(r), message + "（HTTP 状态应为 200）");
        org.junit.jupiter.api.Assertions.assertEquals(200, code(r), message + "（body.code 应为 200，实际 msg: " + msg(r) + "）");
    }
}
