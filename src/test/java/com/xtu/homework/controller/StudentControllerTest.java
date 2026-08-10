package com.xtu.homework.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 学生 Controller 集成测试：作业列表/详情/提交/修改/结果 + 安全断言
 * （详情不得泄露标准答案）+ 教学班边界越权防护。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StudentControllerTest extends BaseControllerTest {

    private static Long homeworkId;         // 教学班1（含自然班1）的作业
    private static Long clazz2OnlyHwId;     // 仅关联自然班2的作业（学生20240001不可见）
    private static Long clazz2OnlyTcId;
    private static Long expiredHwId;        // 已过截止时间的作业
    private static Long appQuestionId;      // 应用题（富文本题干，学生端富文本作答闭环）

    @Test
    @Order(1)
    void testPrepareHomeworks() throws Exception {
        // 创建应用题（富文本题干）——学生端富文本作答闭环用
        MvcResult app = postJson("/api/admin/questions", Map.of(
                "type", "APPLICATION",
                "content", "<p>请分析快速排序的时间复杂度，并写出核心代码</p>",
                "referenceAnswer", "<p>平均 O(n log n)</p>",
                "score", 10, "difficulty", "MEDIUM"), adminToken());
        assertOk(app);
        appQuestionId = body(app).path("data").path("id").asLong();

        // 教师布置作业到教学班 1（关联自然班 1、2，共 6 名学生）
        MvcResult r = postJson("/api/teacher/homeworks", homeworkBody(TEACHING_CLASS_1, "TEST-学生端作业", "2027-12-31T23:59:59"), teacherToken());
        assertOk(r);
        homeworkId = body(r).path("data").path("id").asLong();

        // 教师建"仅关联自然班 2"的教学班并布置作业（学生 20240001 属自然班 1 → 不可见）
        MvcResult tc = postJson("/api/teacher/teaching-classes", Map.of("name", "TEST-仅二班教学班"), teacherToken());
        assertOk(tc);
        clazz2OnlyTcId = body(tc).path("data").path("id").asLong();
        MvcResult link = postJson("/api/teacher/teaching-classes/" + clazz2OnlyTcId + "/classes",
                Map.of("clazzIds", List.of(2)), teacherToken());
        assertOk(link);
        MvcResult hw2 = postJson("/api/teacher/homeworks", homeworkBody(clazz2OnlyTcId, "TEST-仅二班作业", "2027-12-31T23:59:59"), teacherToken());
        assertOk(hw2);
        clazz2OnlyHwId = body(hw2).path("data").path("id").asLong();

        // 已过截止时间的作业（提交应被拒绝）
        MvcResult hw3 = postJson("/api/teacher/homeworks", homeworkBody(TEACHING_CLASS_1, "TEST-过期作业", "2020-01-01T00:00:00"), teacherToken());
        assertOk(hw3);
        expiredHwId = body(hw3).path("data").path("id").asLong();
    }

    @Test
    @Order(2)
    void testListHomeworksShowsAssigned() throws Exception {
        MvcResult r = get("/api/student/homeworks?page=1&size=50", studentToken());
        assertOk(r);
        boolean found = false;
        for (JsonNode n : body(r).path("data").path("records")) {
            if (n.path("id").asLong() == homeworkId) {
                found = true;
                assertEquals("NOT_SUBMITTED", n.path("status").asText(),
                        "未提交学生视角状态应为 NOT_SUBMITTED");
            }
        }
        assertTrue(found, "学生应看到布置给本教学班的作业");
    }

    /** SEC 核心：学生作业详情不得泄露标准答案/参考答案 */
    @Test
    @Order(3)
    void testGetHomeworkDetailNoAnswerLeak() throws Exception {
        MvcResult r = get("/api/student/homeworks/" + homeworkId, studentToken());
        assertOk(r);
        String resp = r.getResponse().getContentAsString();
        assertFalse(resp.contains("\"correctAnswer\""), "详情不得泄露标准答案 correctAnswer");
        assertFalse(resp.contains("\"referenceAnswer\""), "详情不得泄露参考答案 referenceAnswer");
        // 题干/选项/分值正常返回
        JsonNode questions = body(r).path("data").path("questions");
        assertTrue(questions.size() >= 2, "作业应包含至少 2 道题");
        JsonNode first = questions.get(0);
        assertTrue(first.path("type").asText().length() > 0);
        assertTrue(first.path("content").asText().length() > 0);
        if (!"ESSAY".equals(first.path("type").asText())) {
            assertTrue(first.path("options").size() >= 2, "客观题应返回选项");
        }
    }

    @Test
    @Order(4)
    void testSubmitHomework() throws Exception {
        // 应用题答案提交富文本 HTML（学生端 wangeditor getHtml 产物，含代码块）
        String richHtml = "<p>快速排序平均时间复杂度 O(n log n)，最坏 O(n²)。</p><pre><code class=\"language-java\">void quickSort(int[] a,int l,int r){if(l&gt;=r)return;int p=partition(a,l,r);quickSort(a,l,p-1);quickSort(a,p+1,r);}</code></pre>";
        MvcResult r = postJson("/api/student/homeworks/" + homeworkId + "/submit",
                Map.of("answers", List.of(
                        Map.of("questionId", 1, "answer", "C"),
                        Map.of("questionId", 5, "answer", "栈后进先出，队列先进先出"),
                        Map.of("questionId", appQuestionId, "answer", richHtml))),
                studentToken());
        assertOk(r);
        JsonNode data = body(r).path("data");
        assertEquals("SUBMITTED", data.path("status").asText());
        assertEquals(5, data.path("autoScore").asInt(), "客观题 C 正确应得 5 分（应用题主观题不自动判分）");
    }

    @Test
    @Order(5)
    void testModifyAnswerBeforeDeadline() throws Exception {
        // 修改提交必须带全部题目答案（submit 是删旧插新，漏题会抹掉该题已提交答案）
        String modifiedRich = "<p>修改后的应用题答案：快速排序平均 O(n log n)。</p>";
        MvcResult r = postJson("/api/student/homeworks/" + homeworkId + "/submit",
                Map.of("answers", List.of(
                        Map.of("questionId", 1, "answer", "C"),
                        Map.of("questionId", 5, "answer", "修改后的主观题答案"),
                        Map.of("questionId", appQuestionId, "answer", modifiedRich))),
                studentToken());
        assertOk(r);
        assertEquals("SUBMITTED", body(r).path("data").path("status").asText(),
                "截止前修改答案应成功（以最后一次为准）");
    }

    @Test
    @Order(6)
    void testGetResultAfterSubmit() throws Exception {
        MvcResult r = get("/api/student/homeworks/" + homeworkId + "/result", studentToken());
        assertOk(r);
        JsonNode answers = body(r).path("data").path("answers");
        assertTrue(answers.size() >= 3, "结果应包含答案明细（单选+问答题+应用题）");
        boolean appChecked = false, typeFilled = false;
        for (JsonNode a : answers) {
            if (a.path("questionType").asText().length() > 0) typeFilled = true;
            if ("APPLICATION".equals(a.path("questionType").asText())) {
                appChecked = true;
                assertTrue(a.path("studentAnswer").asText().contains("<p>"),
                        "应用题富文本答案应原样返回（供前端 v-html 渲染），实际: " + a.path("studentAnswer").asText().substring(0, Math.min(60, a.path("studentAnswer").asText().length())));
            }
        }
        assertTrue(typeFilled, "结果答案明细应带 questionType（前端按题型区分渲染）");
        assertTrue(appChecked, "结果应包含应用题答案明细");
    }

    /** SEC：学生不能访问/提交不属于当前教学班的作业（自然班 1 学生访问仅含自然班 2 的作业） */
    @Test
    @Order(7)
    void testCannotAccessOtherTeachingClassHomework() throws Exception {
        MvcResult detail = get("/api/student/homeworks/" + clazz2OnlyHwId, studentToken());
        assertEquals(400, code(detail), "非本教学班作业详情应拒绝");
        assertTrue(msg(detail).contains("不属于"), "提示应说明作业不属于当前教学班，实际: " + msg(detail));

        MvcResult submit = postJson("/api/student/homeworks/" + clazz2OnlyHwId + "/submit",
                Map.of("answers", List.of(Map.of("questionId", 1, "answer", "C"))), studentToken());
        assertEquals(400, code(submit), "非本教学班作业提交应拒绝");
    }

    /** 对照：自然班 2 的学生（20240004）可以访问仅含自然班 2 的作业 */
    @Test
    @Order(8)
    void testClazz2StudentCanAccessClazz2OnlyHomework() throws Exception {
        MvcResult r = get("/api/student/homeworks/" + clazz2OnlyHwId, student4Token());
        assertOk(r, "自然班2学生应可访问仅含自然班2的作业");
    }

    /** SEC：超过截止时间提交被拒绝 */
    @Test
    @Order(9)
    void testSubmitAfterDeadlineRejected() throws Exception {
        MvcResult r = postJson("/api/student/homeworks/" + expiredHwId + "/submit",
                Map.of("answers", List.of(Map.of("questionId", 1, "answer", "C"))), studentToken());
        assertEquals(400, code(r), "超截止时间提交应被拒绝");
        assertTrue(msg(r).contains("截止"), "提示应含截止字样，实际: " + msg(r));
    }

    /** 未认证学生接口 → HTTP 401 */
    @Test
    @Order(10)
    void testUnauthenticatedStudentApi401() throws Exception {
        MvcResult r = get("/api/student/homeworks", null);
        assertEquals(401, httpStatus(r));
    }

    /** 学生端富文本适配：作业详情应回显已提交答案（修改答案时编辑器/表单按此初始化） */
    @Test
    @Order(11)
    void testGetHomeworkDetailEchoesAnswer() throws Exception {
        MvcResult r = get("/api/student/homeworks/" + homeworkId, studentToken());
        assertOk(r);
        JsonNode questions = body(r).path("data").path("questions");
        assertTrue(questions.size() >= 3);
        boolean appEchoed = false, essayEchoed = false;
        for (JsonNode q : questions) {
            String type = q.path("type").asText();
            if ("APPLICATION".equals(type)) {
                appEchoed = true;
                assertTrue(q.path("answer").asText().contains("<p>"),
                        "应用题修改回显应返回富文本答案，实际: " + q.path("answer").asText());
            }
            if ("ESSAY".equals(type)) {
                essayEchoed = true;
                assertEquals("修改后的主观题答案", q.path("answer").asText(), "问答题修改回显最近提交的答案");
            }
        }
        assertTrue(appEchoed, "详情应含应用题（answer 字段回显）");
        assertTrue(essayEchoed, "详情应含问答题（answer 字段回显）");
    }

    private Map<String, Object> homeworkBody(Long tcId, String title, String deadline) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("description", "学生端集成测试作业");
        body.put("teachingClassId", tcId);
        body.put("deadline", deadline);
        List<Map<String, Object>> questions = new ArrayList<>();
        questions.add(Map.of("questionId", 1, "sortOrder", 1, "score", 5));
        questions.add(Map.of("questionId", 5, "sortOrder", 2, "score", 15));
        if (appQuestionId != null) {
            questions.add(Map.of("questionId", appQuestionId, "sortOrder", 3, "score", 20));
        }
        body.put("questions", questions);
        return body;
    }
}
