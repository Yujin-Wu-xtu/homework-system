package com.xtu.homework.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 选修教学班（ELECTIVE）集成测试：自由选学生（模拟教务系统选课）、
 * 作业可见性（静态关系，与自然班无关）、移除后访问立即失效、必修/选修边界互斥。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TeacherElectiveTest extends BaseControllerTest {

    private static Long electiveTcId;   // 选修教学班
    private static Long electiveHwId;   // 选修班作业
    private static final Long STUDENT2_ID = 4L;   // 20240002（自然班1，未拉入选修班）

    @Test
    @Order(1)
    void testCreateElectiveTeachingClass() throws Exception {
        MvcResult r = postJson("/api/teacher/teaching-classes",
                Map.of("name", "TEST-选修课-信息安全导论", "courseType", "ELECTIVE"), teacherToken());
        assertOk(r);
        electiveTcId = body(r).path("data").path("id").asLong();
        assertEquals("ELECTIVE", body(r).path("data").path("courseType").asText(), "应保存课程类型 ELECTIVE");
    }

    @Test
    @Order(2)
    void testTeacherCanListAllStudents() throws Exception {
        // 教师可查看全部学生（选课名单），跨自然班
        MvcResult r = get("/api/teacher/students?page=1&size=100", teacherToken());
        assertOk(r);
        boolean found1 = false, found4 = false;
        for (JsonNode n : body(r).path("data").path("records")) {
            long id = n.path("id").asLong();
            if (id == STUDENT_ID) found1 = true;
            if (id == STUDENT4_ID) found4 = true;
        }
        assertTrue(found1 && found4, "教师应能看到不同自然班的学生（选课名单）");
    }

    @Test
    @Order(3)
    void testAddElectiveStudents() throws Exception {
        // 从不同自然班自由拉学生（20240001 自然班1 + 20240004 自然班2）
        MvcResult r = postJson("/api/teacher/teaching-classes/" + electiveTcId + "/students",
                Map.of("studentIds", List.of(STUDENT_ID, STUDENT4_ID)), teacherToken());
        assertOk(r);
        MvcResult list = get("/api/teacher/teaching-classes/" + electiveTcId + "/students", teacherToken());
        assertOk(list);
        assertEquals(2, body(list).path("data").size(), "选修教学班应恰好包含 2 名被选择的学生");
    }

    @Test
    @Order(4)
    void testElectiveClassCannotPullNaturalClasses() throws Exception {
        MvcResult r = postJson("/api/teacher/teaching-classes/" + electiveTcId + "/classes",
                Map.of("clazzIds", List.of(1)), teacherToken());
        assertEquals(400, code(r), "选修教学班不允许按自然班级拉取");
    }

    @Test
    @Order(5)
    void testRequiredClassCannotAddStudentsFreely() throws Exception {
        MvcResult r = postJson("/api/teacher/teaching-classes/1/students",
                Map.of("studentIds", List.of(STUDENT_ID)), teacherToken());
        assertEquals(400, code(r), "必修教学班不允许自由选学生");
    }

    @Test
    @Order(6)
    void testAssignHomeworkToElectiveClass() throws Exception {
        MvcResult r = postJson("/api/teacher/homeworks", homeworkBody("TEST-选修课作业"), teacherToken());
        assertOk(r);
        electiveHwId = body(r).path("data").path("id").asLong();

        // 被拉入的学生（20240001）可见
        MvcResult l1 = get("/api/student/homeworks?page=1&size=50", studentToken());
        assertOk(l1);
        boolean visible1 = false;
        for (JsonNode n : body(l1).path("data").path("records")) {
            if (n.path("id").asLong() == electiveHwId) visible1 = true;
        }
        assertTrue(visible1, "被选入选修班的学生应看到作业");

        // 未拉入的学生（20240002 与 20240001 同自然班1，但不在选修班）不可见——选修是静态选择，不看自然班
        String s2Token = tokenOf(STUDENT2_ID, "20240002", "STUDENT");
        MvcResult l2 = get("/api/student/homeworks?page=1&size=50", s2Token);
        assertOk(l2);
        boolean visible2 = false;
        for (JsonNode n : body(l2).path("data").path("records")) {
            if (n.path("id").asLong() == electiveHwId) visible2 = true;
        }
        assertFalse(visible2, "未选入选修班的学生不应看到作业（同自然班也不可见）");

        MvcResult detail = get("/api/student/homeworks/" + electiveHwId, s2Token);
        assertEquals(400, code(detail), "未选入学生访问作业详情应拒绝");
    }

    @Test
    @Order(7)
    void testRemoveElectiveStudentAccessRevoked() throws Exception {
        MvcResult r = delete("/api/teacher/teaching-classes/" + electiveTcId + "/students/" + STUDENT4_ID, teacherToken());
        assertOk(r);
        // 移除后该学生立即不可见选修班作业（实时鉴权）
        MvcResult list = get("/api/student/homeworks?page=1&size=50", student4Token());
        assertOk(list);
        boolean visible = false;
        for (JsonNode n : body(list).path("data").path("records")) {
            if (n.path("id").asLong() == electiveHwId) visible = true;
        }
        assertFalse(visible, "被移出的学生不应再看到选修班作业");
    }

    /** 选人树：年级→学院→专业→班级→学生；已加入的学生节点 disabled=true（防重复勾选） */
    @Test
    @Order(8)
    void testStudentTreeStructureAndDisabled() throws Exception {
        MvcResult r = get("/api/teacher/student-tree?tcId=" + electiveTcId, teacherToken());
        assertOk(r);
        JsonNode tree = body(r).path("data");
        assertTrue(tree.size() >= 1, "选人树应至少包含一个年级节点");
        // 遍历树找学生节点：20240001(STUDENT_ID=3) 已加入 disabled=true，20240002(id=4) 未加入 disabled=false
        boolean stu1Disabled = false, stu2Disabled = false, stu1Found = false, stu2Found = false;
        java.util.ArrayDeque<JsonNode> queue = new java.util.ArrayDeque<>();
        tree.forEach(queue::add);
        while (!queue.isEmpty()) {
            JsonNode n = queue.poll();
            if (n.path("key").asText().equals("stu:" + STUDENT_ID)) { stu1Found = true; stu1Disabled = n.path("disabled").asBoolean(); }
            if (n.path("key").asText().equals("stu:" + STUDENT2_ID)) { stu2Found = true; stu2Disabled = n.path("disabled").asBoolean(); }
            if (n.has("children")) n.path("children").forEach(queue::add);
        }
        assertTrue(stu1Found && stu2Found, "树中应包含已加入与未加入的学生节点");
        assertTrue(stu1Disabled, "已加入教学班的学生节点应 disabled");
        assertFalse(stu2Disabled, "未加入教学班的学生节点不应 disabled");
    }

    /** 管理员班级树：学院→专业→班级（H2 初始班级无学院 → 归"未分类学院"） */
    @Test
    @Order(9)
    void testAdminClassTree() throws Exception {
        MvcResult r = get("/api/admin/classes/tree", adminToken());
        assertOk(r);
        JsonNode tree = body(r).path("data");
        boolean foundCollege = false, foundClazzNode = false;
        for (JsonNode college : tree) {
            if (college.path("label").asText().equals("未分类学院")) foundCollege = true;
            for (JsonNode major : college.path("children")) {
                for (JsonNode clazz : major.path("children")) {
                    if (clazz.path("clazzId").asLong() > 0) {
                        foundClazzNode = true;
                        assertTrue(clazz.path("studentCount").asLong() >= 0, "班级节点应带学生数");
                    }
                }
            }
        }
        assertTrue(foundCollege, "无学院班级应归'未分类学院'");
        assertTrue(foundClazzNode, "树中应包含班级叶子节点");
    }

    private Map<String, Object> homeworkBody(String title) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("description", "选修教学班集成测试作业");
        body.put("teachingClassId", electiveTcId);
        body.put("deadline", "2027-12-31T23:59:59");
        body.put("questions", List.of(Map.of("questionId", 1, "sortOrder", 1, "score", 5)));
        return body;
    }
}
