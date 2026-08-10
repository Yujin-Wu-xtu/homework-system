package com.xtu.homework.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.xtu.homework.dao.QuestionKnowledgeDao;
import com.xtu.homework.dao.TeachingClassClazzDao;
import com.xtu.homework.dao.UserDao;
import com.xtu.homework.entity.QuestionKnowledge;
import com.xtu.homework.entity.TeachingClassClazz;
import com.xtu.homework.entity.User;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
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
    private static Long appQuestionId;
    @Autowired
    private UserDao userDao;
    @Autowired
    private TeachingClassClazzDao teachingClassClazzDao;
    @Autowired
    private QuestionKnowledgeDao questionKnowledgeDao;

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
        assertEquals(7, data.path("submitTrend").size(), "提交趋势应为近 7 天");
        assertTrue(data.path("questionTypeDist").isArray(), "题型分布应为数组");
        assertTrue(data.path("recentHomeworks").isArray(), "最近作业应为数组");
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
    void testDeleteStudentPhysically() throws Exception {
        MvcResult r = delete("/api/admin/students/" + testStudentId, adminToken());
        assertOk(r);
        // 物理删除：列表不再出现该学生
        MvcResult list = get("/api/admin/students?page=1&size=100", adminToken());
        assertOk(list);
        boolean found = false;
        for (JsonNode n : body(list).path("data").path("records")) {
            if (n.path("id").asLong() == testStudentId) found = true;
        }
        assertFalse(found, "物理删除后学生不应再出现在列表");
        // 原账号不能登录（401/400 均视为拒绝）
        MvcResult login = postJson("/api/auth/login",
                Map.of("username", T_STUDENT_USER, "password", "Admin123456"), null);
        assertNotEquals(200, code(login), "删除后原账号不能登录");
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

        // 外键场景：题目关联该知识点（question_knowledge 外键约束）后仍应可删除，且关联随删
        MvcResult qs = get("/api/admin/questions?page=1&size=1", adminToken());
        long qid = body(qs).path("data").path("records").get(0).path("id").asLong();
        QuestionKnowledge qk = new QuestionKnowledge();
        qk.setQuestionId(qid);
        qk.setKnowledgePointId(testKpId);
        questionKnowledgeDao.insert(qk);

        MvcResult del = delete("/api/admin/knowledge-points/" + testKpId, adminToken());
        assertOk(del);
        Long relLeft = questionKnowledgeDao.selectCount(new LambdaQueryWrapper<QuestionKnowledge>()
                .eq(QuestionKnowledge::getKnowledgePointId, testKpId));
        assertEquals(0L, relLeft, "题目-知识点关联应随知识点删除清理（外键子表）");
    }

    // ========== 应用题（APPLICATION：富文本题干 + 参考答案，主观题链路）==========

    @Test
    @Order(18)
    void testAddApplicationQuestion() throws Exception {
        MvcResult r = postJson("/api/admin/questions", Map.of(
                "type", "APPLICATION",
                "content", "<p>某工厂各季度产量统计如下，请结合图表分析增长趋势</p><div class=\"q-chart\" data-chart='{\"series\":[{\"type\":\"bar\"}]}'>图表</div>",
                "referenceAnswer", "产量逐季度上升，Q4 达到峰值",
                "score", 10, "difficulty", "MEDIUM"), adminToken());
        assertOk(r);
        appQuestionId = body(r).path("data").path("id").asLong();
        assertTrue(appQuestionId > 0);

        MvcResult list = get("/api/admin/questions?page=1&size=100&type=APPLICATION", adminToken());
        assertOk(list);
        boolean found = false;
        for (JsonNode n : body(list).path("data").path("records")) {
            if (n.path("id").asLong() == appQuestionId) {
                found = true;
                assertTrue(n.path("content").asText().contains("<p>"), "应用题题干应保存富文本 HTML");
            }
        }
        assertTrue(found, "应用题应出现在题库列表");
    }

    @Test
    @Order(19)
    void testDeleteApplicationQuestion() throws Exception {
        MvcResult r = delete("/api/admin/questions/" + appQuestionId, adminToken());
        assertOk(r);
    }

    // ========== 删除班级（在册学生拒绝；DISABLED 软删遗留不阻止 + 解除归属）==========

    @Test
    @Order(20)
    void testDeleteClassFlow() throws Exception {
        // 建班级 + 在册学生
        MvcResult c = postJson("/api/admin/classes",
                Map.of("name", "TEST-DEL-CLAZZ", "grade", "2024级", "major", "软件工程"), adminToken());
        assertOk(c);
        long clazzId = body(c).path("data").path("id").asLong();
        MvcResult s = postJson("/api/admin/students",
                Map.of("clazzId", clazzId, "username", "TEST-DEL-STU", "realName", "删除班学生"), adminToken());
        assertOk(s);
        long stuId = body(s).path("data").path("id").asLong();

        // 有在册学生 → 拒绝删除并明确提示
        MvcResult r1 = delete("/api/admin/classes/" + clazzId, adminToken());
        assertEquals(400, code(r1), "有在册学生应拒绝删除");
        assertTrue(msg(r1).contains("在册学生"), "提示应包含在册学生，实际: " + msg(r1));

        // 禁用该学生（模拟软删遗留账号）→ 班级应可删，且禁用学生解除班级归属
        User u = new User();
        u.setId(stuId);
        u.setStatus("DISABLED");
        userDao.updateById(u);
        // 教学班关联该班级（外键约束场景：teaching_class_clazz 引用 clazz）
        TeachingClassClazz rel = new TeachingClassClazz();
        rel.setTeachingClassId(1L);
        rel.setClazzId(clazzId);
        teachingClassClazzDao.insert(rel);
        MvcResult r2 = delete("/api/admin/classes/" + clazzId, adminToken());
        assertOk(r2);
        MvcResult tree = get("/api/admin/classes/tree", adminToken());
        boolean found = false;
        for (JsonNode college : body(tree).path("data")) {
            for (JsonNode major : college.path("children")) {
                for (JsonNode cl : major.path("children")) {
                    if (cl.path("clazzId").asLong() == clazzId) found = true;
                }
            }
        }
        assertFalse(found, "删除后班级树不应再有该班级");
        User after = userDao.selectById(stuId);
        assertNotNull(after, "禁用学生账号应保留");
        assertNull(after.getClazzId(), "禁用学生应解除班级归属");
        Long relLeft = teachingClassClazzDao.selectCount(new LambdaQueryWrapper<TeachingClassClazz>()
                .eq(TeachingClassClazz::getClazzId, clazzId));
        assertEquals(0L, relLeft, "教学班-自然班关联记录应随班级删除清理（外键子表）");
    }
}
