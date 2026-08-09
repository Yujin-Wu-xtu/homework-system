package com.xtu.homework.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 管理员 Controller 集成测试：统计/教师/学生/题目/知识点 CRUD + 敏感字段（密码哈希）不泄露。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminControllerTest extends BaseControllerTest {

    private static Long testTeacherId;
    private static Long testStudentId;
    private static Long testQuestionId;
    private static Long testKpId;

    private static final String T_TEACHER_USER = "TEST-T-01";
    private static final String T_STUDENT_USER = "TEST-S-01";

    // ========== 首页统计 ==========

    @Test
    @Order(1)
    void testDashboardReturnsStats() throws Exception {
        MvcResult r = get("/api/admin/dashboard", adminToken());
        assertOk(r);
        JsonNode data = body(r).path("data");
        assertTrue(data.path("classCount").asLong() >= 2, "班级数应≥2");
        assertTrue(data.path("studentCount").asLong() >= 6, "学生数应≥6");
        assertTrue(data.path("teacherCount").asLong() >= 1);
        assertTrue(data.path("questionCount").asLong() >= 5);
    }

    // ========== 教师管理 ==========

    @Test
    @Order(2)
    void testAddTeacherSuccessAndNoPasswordLeak() throws Exception {
        MvcResult r = postJson("/api/admin/teachers", Map.of(
                "username", T_TEACHER_USER, "realName", "测试教师", "phone", "13900000001"), adminToken());
        assertOk(r);
        testTeacherId = body(r).path("data").path("id").asLong();
        assertTrue(testTeacherId > 0);
        assertFalse(r.getResponse().getContentAsString().contains("\"password\""),
                "新增教师响应不得包含 password 字段（密码哈希泄露）");
    }

    @Test
    @Order(3)
    void testAddTeacherDuplicateRejected() throws Exception {
        MvcResult r = postJson("/api/admin/teachers", Map.of(
                "username", T_TEACHER_USER, "realName", "重复教师"), adminToken());
        assertEquals(200, httpStatus(r));
        assertEquals(400, code(r), "重复工号应返回 400");
        assertTrue(msg(r).contains("已存在"), "应提示工号已存在，实际: " + msg(r));
    }

    @Test
    @Order(4)
    void testListTeachersNoPasswordLeak() throws Exception {
        MvcResult r = get("/api/admin/teachers?page=1&size=20", adminToken());
        assertOk(r);
        assertFalse(r.getResponse().getContentAsString().contains("\"password\""),
                "教师列表响应不得包含 password 字段");
    }

    @Test
    @Order(5)
    void testUpdateTeacher() throws Exception {
        MvcResult r = putJson("/api/admin/teachers/" + testTeacherId,
                Map.of("realName", "测试教师改"), adminToken());
        assertOk(r);
    }

    @Test
    @Order(6)
    void testResetTeacherPwd() throws Exception {
        MvcResult r = putJson("/api/admin/teachers/" + testTeacherId + "/reset-pwd",
                Map.of(), adminToken());
        assertOk(r);
        assertTrue(body(r).path("data").path("newPassword").asText().length() >= 8,
                "重置密码应返回新密码");
    }

    @Test
    @Order(7)
    void testDeleteTeacherSoftDelete() throws Exception {
        MvcResult r = delete("/api/admin/teachers/" + testTeacherId, adminToken());
        assertOk(r);
        // 软删除：教师仍在列表但状态为 DISABLED（保留历史引用完整性）
        MvcResult list = get("/api/admin/teachers?page=1&size=50&keyword=TEST-T-01", adminToken());
        boolean foundDisabled = false;
        for (JsonNode n : body(list).path("data").path("records")) {
            if ("TEST-T-01".equals(n.path("username").asText())) {
                assertEquals("DISABLED", n.path("status").asText(), "软删除后状态应为 DISABLED");
                foundDisabled = true;
            }
        }
        assertTrue(foundDisabled, "软删除后教师仍应出现在列表（状态 DISABLED）");
    }

    // ========== 学生管理 ==========

    @Test
    @Order(8)
    void testAddStudentSuccessAndNoPasswordLeak() throws Exception {
        MvcResult r = postJson("/api/admin/students", Map.of(
                "clazzId", 1, "username", T_STUDENT_USER, "realName", "测试学生",
                "phone", "13900000002", "email", "test-s-01@example.com"), adminToken());
        assertOk(r);
        testStudentId = body(r).path("data").path("id").asLong();
        assertTrue(testStudentId > 0);
        assertFalse(r.getResponse().getContentAsString().contains("\"password\""),
                "新增学生响应不得包含 password 字段");
        assertEquals(1, body(r).path("data").path("clazzId").asLong());
    }

    @Test
    @Order(9)
    void testAddStudentDuplicateRejected() throws Exception {
        MvcResult r = postJson("/api/admin/students", Map.of(
                "clazzId", 1, "username", T_STUDENT_USER, "realName", "重复学号"), adminToken());
        assertEquals(400, code(r), "重复学号应返回 400");
    }

    @Test
    @Order(10)
    void testListStudentsNoPasswordLeak() throws Exception {
        MvcResult r = get("/api/admin/students?page=1&size=50", adminToken());
        assertOk(r);
        assertFalse(r.getResponse().getContentAsString().contains("\"password\""),
                "学生列表响应不得包含 password 字段");
    }

    /** 转班：TEST-S-01 从自然班 1 → 自然班 2 */
    @Test
    @Order(11)
    void testTransferStudent() throws Exception {
        MvcResult r = putJson("/api/admin/classes/1/students/" + testStudentId + "/transfer",
                Map.of("toClazzId", 2), adminToken());
        assertOk(r);
        MvcResult list = get("/api/admin/students?page=1&size=50&clazzId=2", adminToken());
        boolean found = false;
        for (JsonNode n : body(list).path("data").path("records")) {
            if (n.path("id").asLong() == testStudentId) found = true;
        }
        assertTrue(found, "转班后学生应出现在新班级列表");
    }

    @Test
    @Order(12)
    void testDeleteStudentSoftDelete() throws Exception {
        MvcResult r = delete("/api/admin/students/" + testStudentId, adminToken());
        assertOk(r);
        assertTrue(msg(r).contains("禁用"), "删除学生应提示账号禁用");
    }

    // ========== 题库 ==========

    @Test
    @Order(13)
    void testQuestionDuplicateCheck() throws Exception {
        MvcResult r = postJson("/api/admin/questions/check-duplicate",
                Map.of("content", "以下哪种数据结构是线性结构？", "type", "SINGLE_CHOICE"), adminToken());
        assertOk(r);
        assertTrue(body(r).path("data").isArray() && body(r).path("data").size() > 0,
                "与已有题目重复应返回重复明细");
    }

    @Test
    @Order(14)
    void testAddQuestion() throws Exception {
        Map<String, Object> q = new HashMap<>();
        q.put("type", "SINGLE_CHOICE");
        q.put("content", "TEST-集成测试题目-单选题");
        q.put("correctAnswer", "A");
        q.put("score", 5);
        q.put("difficulty", "EASY");
        q.put("options", List.of(
                Map.of("label", "A", "content", "选项甲", "sortOrder", 1),
                Map.of("label", "B", "content", "选项乙", "sortOrder", 2)));
        q.put("knowledgePointIds", List.of(1));
        MvcResult r = postJson("/api/admin/questions", q, adminToken());
        assertOk(r);
        testQuestionId = body(r).path("data").path("id").asLong();
        assertTrue(testQuestionId > 0);
    }

    @Test
    @Order(15)
    void testUpdateQuestion() throws Exception {
        MvcResult r = putJson("/api/admin/questions/" + testQuestionId,
                Map.of("content", "TEST-集成测试题目-已编辑", "difficulty", "MEDIUM",
                        "correctAnswer", "A", "options", List.of(
                                Map.of("label", "A", "content", "选项甲改", "sortOrder", 1),
                                Map.of("label", "B", "content", "选项乙改", "sortOrder", 2))),
                adminToken());
        assertOk(r);
    }

    @Test
    @Order(16)
    void testDeleteQuestion() throws Exception {
        MvcResult r = delete("/api/admin/questions/" + testQuestionId, adminToken());
        assertOk(r);
    }

    // ========== 知识点 ==========

    @Test
    @Order(17)
    void testKnowledgePointCrud() throws Exception {
        MvcResult add = postJson("/api/admin/knowledge-points",
                Map.of("name", "TEST-集成测试知识点", "subject", "软件测试"), adminToken());
        assertOk(add);
        testKpId = body(add).path("data").path("id").asLong();
        assertTrue(testKpId > 0);

        MvcResult upd = putJson("/api/admin/knowledge-points/" + testKpId,
                Map.of("name", "TEST-集成测试知识点改"), adminToken());
        assertOk(upd);

        MvcResult list = get("/api/admin/knowledge-points?keyword=TEST-集成测试知识点改", adminToken());
        boolean found = false;
        for (JsonNode n : body(list).path("data")) {
            if (n.path("id").asLong() == testKpId) found = true;
        }
        assertTrue(found, "修改后知识点应能检索到");

        MvcResult del = delete("/api/admin/knowledge-points/" + testKpId, adminToken());
        assertOk(del);
    }
}
