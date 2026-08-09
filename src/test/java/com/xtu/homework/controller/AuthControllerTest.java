package com.xtu.homework.controller;

import com.xtu.homework.dao.UserDao;
import com.xtu.homework.entity.User;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 认证安全 Controller 集成测试（真实登录链路 + 鉴权边界 + SQL 注入 + 锁定策略）。
 *
 * 说明：不依赖初始账号密码（Service 测试会重置 admin 密码），登录类用例全部使用
 * 本类自建、自清理的临时账号。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthControllerTest extends BaseControllerTest {

    @Autowired
    private UserDao userDao;
    @Autowired
    private PasswordEncoder passwordEncoder;

    /** 临时登录测试账号（真实插入 DB，密码已知：TestPass123） */
    private static Long tempUserId;
    private static final String TEMP_USERNAME = "TEST-AUTH-01";
    private static final String TEMP_PASSWORD = "TestPass123";

    @Test
    @Order(1)
    void testLoginSuccess() throws Exception {
        createTempUser();
        MvcResult r = postJson("/api/auth/login",
                Map.of("username", TEMP_USERNAME, "password", TEMP_PASSWORD), null);
        assertOk(r);
        assertTrue(body(r).path("data").path("token").asText().length() > 20, "应返回 JWT token");
        assertEquals("STUDENT", body(r).path("data").path("role").asText());
    }

    @Test
    @Order(2)
    void testLoginWrongPasswordRejected() throws Exception {
        MvcResult r = postJson("/api/auth/login",
                Map.of("username", TEMP_USERNAME, "password", "WrongPass999"), null);
        assertEquals(200, httpStatus(r));
        assertEquals(401, code(r), "错误密码应返回业务码 401");
    }

    /** SEC：SQL 注入用户名不得绕过认证，且不得泄露 SQL/异常信息 */
    @Test
    @Order(3)
    void testLoginSqlInjectionRejected() throws Exception {
        MvcResult r = postJson("/api/auth/login",
                Map.of("username", "admin' OR '1'='1", "password", "x"), null);
        assertEquals(401, code(r), "SQL 注入用户名必须登录失败");
        String resp = r.getResponse().getContentAsString();
        assertFalse(resp.toLowerCase().contains("sql"), "响应不应泄露 SQL 信息");
        assertFalse(resp.contains("Exception"), "响应不应泄露异常堆栈");
        assertFalse(resp.contains("syntax"), "响应不应包含数据库错误");
    }

    /** @Valid 校验：空用户名 → GlobalExceptionHandler 转 R 400（而非 Spring 默认错误体） */
    @Test
    @Order(4)
    void testLoginBlankUsernameReturns400() throws Exception {
        MvcResult r = postJson("/api/auth/login",
                Map.of("username", "", "password", "x"), null);
        assertEquals(200, httpStatus(r));
        assertEquals(400, code(r));
        assertTrue(msg(r).contains("用户名"), "应返回用户可理解的校验消息，实际: " + msg(r));
    }

    /** SEC：未携带 token 访问受保护接口 → 真实 HTTP 401 */
    @Test
    @Order(5)
    void testUnauthenticatedAccessReturns401() throws Exception {
        MvcResult r = get("/api/admin/dashboard", null);
        assertEquals(401, httpStatus(r), "未认证访问应返回 HTTP 401");
    }

    /** SEC：跨角色越权 → 学生 token 访问管理员接口 → 真实 HTTP 403 */
    @Test
    @Order(6)
    void testStudentTokenOnAdminApiReturns403() throws Exception {
        MvcResult r = get("/api/admin/dashboard", studentToken());
        assertEquals(403, httpStatus(r), "学生访问管理员接口应返回 HTTP 403");
    }

    /** SEC：教师 token 访问学生接口 → HTTP 403 */
    @Test
    @Order(7)
    void testTeacherTokenOnStudentApiReturns403() throws Exception {
        MvcResult r = get("/api/student/homeworks", teacherToken());
        assertEquals(403, httpStatus(r), "教师访问学生接口应返回 HTTP 403");
    }

    /** SEC：伪造/篡改 token → 401（JWT 签名校验） */
    @Test
    @Order(8)
    void testTamperedTokenRejected() throws Exception {
        String tampered = studentToken().substring(0, studentToken().length() - 3) + "abc";
        MvcResult r = get("/api/student/homeworks", tampered);
        assertEquals(401, httpStatus(r), "篡改的 JWT 应返回 HTTP 401");
    }

    /** SEC：连续 5 次错误密码 → 账号锁定（第 5 次即提示锁定，正确密码也被拒） */
    @Test
    @Order(9)
    void testLoginLockoutAfterFiveFailures() throws Exception {
        String wrong = "WrongPass" + System.currentTimeMillis();
        String lockMsg = "";
        for (int i = 1; i <= 5; i++) {
            MvcResult r = postJson("/api/auth/login",
                    Map.of("username", TEMP_USERNAME, "password", wrong), null);
            assertEquals(401, code(r));
            if (i == 5) lockMsg = msg(r);
        }
        assertTrue(lockMsg.contains("锁定"), "第 5 次失败应提示锁定，实际: " + lockMsg);
        // 锁定期间正确密码也被拒绝
        MvcResult r2 = postJson("/api/auth/login",
                Map.of("username", TEMP_USERNAME, "password", TEMP_PASSWORD), null);
        assertEquals(401, code(r2), "锁定期间正确密码也应被拒");
        assertTrue(msg(r2).contains("锁定"));
        // 解除锁定（恢复临时账号可用状态，避免影响其他用例）
        User u = userDao.selectById(tempUserId);
        u.setLockedUntil(null);
        u.setLoginFailCount(0);
        userDao.updateById(u);
    }

    /** SEC：安全响应头（nosniff / frame / referrer-policy）*/
    @Test
    @Order(10)
    void testSecurityHeadersPresent() throws Exception {
        MvcResult r = postJson("/api/auth/login",
                Map.of("username", TEMP_USERNAME, "password", TEMP_PASSWORD), null);
        assertEquals(200, httpStatus(r));
        assertEquals("nosniff", r.getResponse().getHeader("X-Content-Type-Options"));
        assertNotNull(r.getResponse().getHeader("X-Frame-Options"), "应返回 X-Frame-Options");
        assertNotNull(r.getResponse().getHeader("Referrer-Policy"), "应返回 Referrer-Policy");
    }

    /** 注册：无有效验证码 → 400 明确提示（校验链第一环） */
    @Test
    @Order(11)
    void testRegisterWithoutValidCodeRejected() throws Exception {
        MvcResult r = postJson("/api/auth/register", Map.of(
                "username", "TEST-REG-01",
                "email", "test-reg-01@example.com",
                "password", "StrongPass1",
                "code", "000000"), null);
        assertEquals(200, httpStatus(r));
        assertEquals(400, code(r));
        assertTrue(msg(r).contains("验证码"), "应提示验证码无效，实际: " + msg(r));
    }

    @AfterAll
    static void cleanup() {
        // 临时账号留在 H2 内存库（每轮 mvn test 全新，账号名唯一），不影响其他测试类
    }

    private void createTempUser() {
        if (tempUserId != null) return;
        User u = new User();
        u.setUsername(TEMP_USERNAME);
        u.setPassword(passwordEncoder.encode(TEMP_PASSWORD));
        u.setRealName("认证测试账号");
        u.setRole("STUDENT");
        u.setStatus("ACTIVE");
        u.setPwdResetRequired(false);
        userDao.insert(u);
        tempUserId = u.getId();
        assertNotNull(tempUserId);
    }
}
