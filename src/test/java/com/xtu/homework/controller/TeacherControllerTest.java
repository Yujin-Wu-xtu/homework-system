package com.xtu.homework.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 教师 Controller 集成测试：教学班 CRUD / 作业发布与评分链路 / 教师间越权（IDOR）防护。
 *
 * 越权场景使用第二个教师账号（TEST-T-02，管理员创建）访问初始教师（T2024001）的资源。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TeacherControllerTest extends BaseControllerTest {

    private static Long teacherBId;        // 越权方教师（TEST-T-02）
    private static String teacherBToken;
    private static Long testTcId;          // 教师 A 新建教学班
    private static Long emptyTcId;         // 空教学班
    private static Long homeworkId;        // 教师 A 发布的作业
    private static Long essayAnswerId;     // 学生提交的主观题答案 id（评分目标）

    @Test
    @Order(1)
    void testCreateSecondTeacherForIdor() throws Exception {
        MvcResult r = postJson("/api/admin/teachers", Map.of(
                "username", "TEST-T-02", "realName", "越权测试教师"), adminToken());
        assertOk(r);
        teacherBId = body(r).path("data").path("id").asLong();
        assertTrue(teacherBId > 0);
        teacherBToken = tokenOf(teacherBId, "TEST-T-02", "TEACHER");
    }

    // ========== 教学班管理 ==========

    @Test
    @Order(2)
    void testCreateTeachingClass() throws Exception {
        MvcResult r = postJson("/api/teacher/teaching-classes",
                Map.of("name", "TEST-集成测试教学班"), teacherToken());
        assertOk(r);
        testTcId = body(r).path("data").path("id").asLong();
        assertTrue(testTcId > 0);
        assertEquals(TEACHER_ID, body(r).path("data").path("teacherId").asLong(),
                "教学班教师应自动绑定为当前教师（防冒充他人教学班）");
    }

    @Test
    @Order(3)
    void testAddClassesToTeachingClass() throws Exception {
        MvcResult r = postJson("/api/teacher/teaching-classes/" + testTcId + "/classes",
                Map.of("clazzIds", List.of(1)), teacherToken());
        assertOk(r);
        assertEquals(1, body(r).path("data").path("added").asInt());
    }

    /** 教学班学生列表不得泄露密码哈希 */
    @Test
    @Order(4)
    void testTeachingClassStudentsNoPasswordLeak() throws Exception {
        MvcResult r = get("/api/teacher/teaching-classes/" + testTcId + "/students", teacherToken());
        assertOk(r);
        assertFalse(r.getResponse().getContentAsString().contains("\"password\""),
                "教学班学生列表不得包含 password 字段");
        assertTrue(body(r).path("data").size() >= 3, "关联自然班1应至少有 3 名学生");
    }

    /** 教师 A 不能查看教师 B 的教学班（教学班归属校验） */
    @Test
    @Order(5)
    void testOtherTeacherCannotAccessTeachingClass() throws Exception {
        MvcResult r = get("/api/teacher/teaching-classes/" + testTcId + "/students", teacherBToken);
        assertEquals(400, code(r), "非本教师教学班应拒绝访问");
    }

    @Test
    @Order(6)
    void testResetSingleStudentPwd() throws Exception {
        // 取教学班内第一个学生（初始 20240001）
        MvcResult list = get("/api/teacher/teaching-classes/" + testTcId + "/students", teacherToken());
        JsonNode first = body(list).path("data").get(0);
        long sid = first.path("id").asLong();
        MvcResult r = putJson("/api/teacher/teaching-classes/" + testTcId + "/students/" + sid + "/reset-pwd",
                Map.of(), teacherToken());
        assertOk(r);
        assertTrue(body(r).path("data").path("newPassword").asText().length() >= 8,
                "重置密码应返回新密码");
    }

    /** 教师 A 不能重置教师 B 教学班内学生密码 */
    @Test
    @Order(7)
    void testOtherTeacherCannotResetStudentPwd() throws Exception {
        MvcResult r = putJson("/api/teacher/teaching-classes/" + testTcId + "/students/3/reset-pwd",
                Map.of(), teacherBToken);
        assertEquals(400, code(r), "非本教师教学班学生不可重置密码");
    }

    // ========== 作业管理 ==========

    @Test
    @Order(8)
    void testAssignHomeworkToEmptyClassRejected() throws Exception {
        MvcResult tc = postJson("/api/teacher/teaching-classes",
                Map.of("name", "TEST-空教学班"), teacherToken());
        assertOk(tc);
        emptyTcId = body(tc).path("data").path("id").asLong();

        MvcResult r = postJson("/api/teacher/homeworks", homeworkBody(emptyTcId, "TEST-空班作业"), teacherToken());
        assertEquals(400, code(r), "空教学班发布作业应被拒绝");
        assertTrue(msg(r).contains("尚未包含"), "应提示教学班无学生，实际: " + msg(r));
    }

    @Test
    @Order(9)
    void testAssignHomeworkSuccess() throws Exception {
        MvcResult r = postJson("/api/teacher/homeworks", homeworkBody(TEACHING_CLASS_1, "TEST-集成测试作业"), teacherToken());
        assertOk(r);
        homeworkId = body(r).path("data").path("id").asLong();
        assertTrue(homeworkId > 0);
        // 发布后自动生成该教学班学生的待提交记录（学生可见性前提）
        MvcResult subs = get("/api/teacher/homeworks/" + homeworkId + "/submissions", teacherToken());
        assertOk(subs);
        assertTrue(body(subs).path("data").size() >= 6, "教学班1关联两个自然班共 6 名学生");
    }

    // ========== 越权防护（IDOR）：教师 B 访问教师 A 的作业 ==========

    @Test
    @Order(10)
    void testOtherTeacherCannotViewHomeworkDetail() throws Exception {
        MvcResult r = get("/api/teacher/homeworks/" + homeworkId + "/detail", teacherBToken);
        assertEquals(400, code(r), "他人作业详情应拒绝访问");
        assertTrue(msg(r).contains("无权"), "提示应含无权字样，实际: " + msg(r));
    }

    @Test
    @Order(11)
    void testOtherTeacherCannotViewSubmissions() throws Exception {
        MvcResult r = get("/api/teacher/homeworks/" + homeworkId + "/submissions", teacherBToken);
        assertEquals(400, code(r), "他人作业提交情况应拒绝访问");
    }

    @Test
    @Order(12)
    void testOtherTeacherCannotUpdateOrDeleteHomework() throws Exception {
        MvcResult upd = putJson("/api/teacher/homeworks/" + homeworkId,
                homeworkBody(TEACHING_CLASS_1, "TEST-篡改作业"), teacherBToken);
        assertEquals(400, code(upd), "他人作业不可修改");
        MvcResult del = delete("/api/teacher/homeworks/" + homeworkId, teacherBToken);
        assertEquals(400, code(del), "他人作业不可删除");
    }

    @Test
    @Order(13)
    void testOtherTeacherCannotAssignToOwnedClass() throws Exception {
        // 教师 B 不能向教师 A 的教学班（id=1）发布作业
        MvcResult r = postJson("/api/teacher/homeworks", homeworkBody(TEACHING_CLASS_1, "TEST-B越权发布"), teacherBToken);
        assertEquals(400, code(r), "向他人教学班发布作业应被拒绝");
    }

    @Test
    @Order(14)
    void testOtherTeacherCannotGradeOrExport() throws Exception {
        MvcResult grading = get("/api/teacher/homeworks/" + homeworkId + "/grading", teacherBToken);
        assertEquals(400, code(grading), "他人作业评分列表应拒绝访问");
        MvcResult export = get("/api/teacher/homeworks/" + homeworkId + "/export", teacherBToken);
        assertEquals(400, httpStatus(export), "他人作业成绩导出应返回 400");
    }

    @Test
    @Order(15)
    void testNonexistentHomeworkRejected() throws Exception {
        MvcResult r = get("/api/teacher/homeworks/99999/detail", teacherToken());
        assertEquals(400, code(r), "不存在的作业应返回 400");
    }

    // ========== 评分闭环（学生提交 → 教师评分 → 导出）==========

    @Test
    @Order(16)
    void testStudentSubmitThenTeacherGrade() throws Exception {
        // 学生 20240001 提交（客观题 C 正确得 5 分 + 主观题作答）
        MvcResult submit = postJson("/api/student/homeworks/" + homeworkId + "/submit",
                Map.of("answers", List.of(
                        Map.of("questionId", 1, "answer", "C"),
                        Map.of("questionId", 5, "answer", "栈是后进先出，队列是先进先出"))),
                studentToken());
        assertOk(submit);
        assertEquals("SUBMITTED", body(submit).path("data").path("status").asText());

        // 未批改过滤应命中该学生
        MvcResult grading = get("/api/teacher/homeworks/" + homeworkId + "/grading?status=UNGRADED", teacherToken());
        assertOk(grading);
        boolean found = false;
        for (JsonNode n : body(grading).path("data").path("records")) {
            if (n.path("studentId").asLong() == STUDENT_ID) found = true;
        }
        assertTrue(found, "未批改列表应包含已提交学生");

        // 取主观题答案 id 并评分
        MvcResult subs = get("/api/teacher/homeworks/" + homeworkId + "/submissions", teacherToken());
        JsonNode subRecord = null;
        for (JsonNode n : body(subs).path("data")) {
            if (n.path("studentId").asLong() == STUDENT_ID) subRecord = n;
        }
        assertNotNull(subRecord, "提交记录应存在");
        long subId = subRecord.path("submissionId").asLong();
        MvcResult answers = get("/api/teacher/homeworks/" + homeworkId + "/submissions/" + subId + "/answers", teacherToken());
        assertOk(answers);
        for (JsonNode a : body(answers).path("data")) {
            if ("ESSAY".equals(a.path("questionType").asText())) {
                essayAnswerId = a.path("id").asLong();
            }
        }
        assertNotNull(essayAnswerId, "应找到主观题答案记录");

        MvcResult grade = putJson("/api/teacher/homeworks/" + homeworkId + "/grading/" + essayAnswerId,
                Map.of("score", 15, "comment", "答得不错"), teacherToken());
        assertOk(grade);

        // 已批改过滤应命中
        MvcResult graded = get("/api/teacher/homeworks/" + homeworkId + "/grading?status=GRADED", teacherToken());
        assertOk(graded);
        boolean gradedFound = false;
        for (JsonNode n : body(graded).path("data").path("records")) {
            if (n.path("studentId").asLong() == STUDENT_ID) gradedFound = true;
        }
        assertTrue(gradedFound, "已批改列表应包含该学生");
    }

    @Test
    @Order(17)
    void testExportGradesExcel() throws Exception {
        MvcResult r = get("/api/teacher/homeworks/" + homeworkId + "/export", teacherToken());
        assertEquals(200, httpStatus(r));
        String ct = r.getResponse().getContentType();
        assertTrue(ct != null && ct.contains("octet-stream"), "导出应为 Excel 二进制流，实际: " + ct);
        assertTrue(r.getResponse().getContentAsByteArray().length > 100, "导出文件应有内容");
    }

    @Test
    @Order(18)
    void testDeleteOwnHomework() throws Exception {
        // 已有学生提交 → 删除被拒绝（保护逻辑）
        MvcResult r = delete("/api/teacher/homeworks/" + homeworkId, teacherToken());
        assertEquals(400, code(r), "已有学生提交的作业不可删除");
        assertTrue(msg(r).contains("提交"), "应提示已有学生提交，实际: " + msg(r));
    }

    @Test
    @Order(19)
    void testDeleteTeachingClass() throws Exception {
        MvcResult r = delete("/api/teacher/teaching-classes/" + emptyTcId, teacherToken());
        assertOk(r);
    }

    private Map<String, Object> homeworkBody(Long tcId, String title) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("description", "集成测试用作业");
        body.put("teachingClassId", tcId);
        body.put("deadline", "2027-12-31T23:59:59");
        body.put("questions", List.of(
                Map.of("questionId", 1, "sortOrder", 1, "score", 5),
                Map.of("questionId", 5, "sortOrder", 2, "score", 15)));
        return body;
    }
}
