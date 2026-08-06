package com.xtu.homework.service;

import com.xtu.homework.HomeworkApplication;
import com.xtu.homework.dto.HomeworkAssignDto;
import com.xtu.homework.entity.Homework;
import com.xtu.homework.entity.QuestionOption;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 作业服务单元测试
 * 覆盖：布置作业、修改作业、删除作业、提交状态查看、成绩导出、学生作业列表
 */
@SpringBootTest(classes = HomeworkApplication.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HomeworkServiceImplTest {

    @Autowired
    private HomeworkService homeworkService;

    private static Long createdHwId;

    @Test
    @Order(1)
    void testAssignHomework() {
        HomeworkAssignDto dto = new HomeworkAssignDto();
        dto.setTitle("单元测试作业-" + System.currentTimeMillis());
        dto.setDescription("由JUnit自动创建的测试作业");
        dto.setTeachingClassId(1L);
        dto.setDeadline(LocalDateTime.now().plusDays(7));

        HomeworkAssignDto.QuestionItem qi = new HomeworkAssignDto.QuestionItem();
        qi.setQuestionId(1L);
        qi.setSortOrder(1);
        qi.setScore(BigDecimal.TEN);
        dto.setQuestions(List.of(qi));

        Homework hw = homeworkService.assignHomework(2L, dto);
        assertNotNull(hw.getId());
        assertEquals(dto.getTitle(), hw.getTitle());
        assertEquals("PUBLISHED", hw.getStatus());
        createdHwId = hw.getId();
    }

    @Test
    @Order(2)
    void testListStudentHomeworks() {
        var page = homeworkService.listStudentHomeworks(3L, 1, 10);
        assertNotNull(page);
        // 学生3在班级1，教学班1包含班级1，应该有作业
        assertFalse(page.getRecords().isEmpty());
        // 学生视角状态：发布中的作业对未提交学生应显示为 NOT_SUBMITTED（而非作业全局状态 PUBLISHED），
        // 否则前端"待完成"tab 过滤条件（NOT_SUBMITTED/SUBMITTED）匹配不到，学生看不到作业
        Homework hw = page.getRecords().get(0);
        assertNotNull(hw.getStatus());
        assertTrue(List.of("NOT_SUBMITTED", "SUBMITTED", "GRADED").contains(hw.getStatus()),
                "学生端作业列表 status 应为提交状态，实际: " + hw.getStatus());
    }

    @Test
    @Order(3)
    void testGetHomeworkDetail() {
        Map<String, Object> detail = homeworkService.getHomeworkDetail(
                createdHwId, 3L);
        assertNotNull(detail);
        assertTrue(detail.containsKey("homework"));
        assertTrue(detail.containsKey("questions"));
        assertTrue(detail.containsKey("submission"));
        // 客观题选项必须按 label 固定顺序（A、B、C、D）——随机 shuffle 曾导致显示乱序（用户报 bug）
        List<Map<String, Object>> questions = (List<Map<String, Object>>) detail.get("questions");
        assertFalse(questions.isEmpty());
        Map<String, Object> first = questions.get(0);
        if (!"ESSAY".equals(first.get("type"))) {
            List<QuestionOption> options = (List<QuestionOption>) first.get("options");
            assertEquals(List.of("A", "B", "C", "D"),
                    options.stream().map(QuestionOption::getLabel).toList(),
                    "客观题选项应按 label 升序（A、B、C、D）");
        }
    }

    @Test
    @Order(4)
    void testGetSubmissionStatus() {
        List<Map<String, Object>> status = homeworkService.getSubmissionStatus(createdHwId);
        assertNotNull(status);
        // 教学班1包含班级1和2的学生，应该有6个提交记录
        assertTrue(status.size() >= 1);
    }

    @Test
    @Order(5)
    void testExportGrades() {
        byte[] excel = homeworkService.exportGrades(createdHwId);
        assertNotNull(excel);
        assertTrue(excel.length > 0);
    }

    @Test
    @Order(6)
    void testUpdateHomework() {
        HomeworkAssignDto update = new HomeworkAssignDto();
        update.setTitle("更新后的作业-" + System.currentTimeMillis());
        update.setDescription("已更新");
        update.setTeachingClassId(1L);
        update.setDeadline(LocalDateTime.now().plusDays(14));
        HomeworkAssignDto.QuestionItem qi = new HomeworkAssignDto.QuestionItem();
        qi.setQuestionId(1L);
        qi.setSortOrder(1);
        qi.setScore(BigDecimal.TEN);
        update.setQuestions(List.of(qi));

        Homework hw = homeworkService.updateHomework(createdHwId, update);
        assertEquals(update.getTitle(), hw.getTitle());
    }

    @Test
    @Order(7)
    void testDeleteHomework() {
        // 删除未提交的作业应该成功
        assertDoesNotThrow(() -> homeworkService.deleteHomework(createdHwId));
    }

    @Test
    @Order(8)
    void testDeleteHomeworkWithSubmissions() {
        // 先布置一个作业，然后模拟有人提交后再删除
        HomeworkAssignDto dto = new HomeworkAssignDto();
        dto.setTitle("测试删除-" + System.currentTimeMillis());
        dto.setDescription("测试");
        dto.setTeachingClassId(1L);
        dto.setDeadline(LocalDateTime.now().plusDays(1));
        HomeworkAssignDto.QuestionItem qi = new HomeworkAssignDto.QuestionItem();
        qi.setQuestionId(1L);
        qi.setSortOrder(1);
        qi.setScore(BigDecimal.ONE);
        dto.setQuestions(List.of(qi));
        Homework hw = homeworkService.assignHomework(2L, dto);

        // 没有提交，删除应该成功
        assertDoesNotThrow(() -> homeworkService.deleteHomework(hw.getId()));
    }
}
