package com.xtu.homework.service;

import com.xtu.homework.HomeworkApplication;
import com.xtu.homework.dto.HomeworkAssignDto;
import com.xtu.homework.dto.SubmissionDto;
import com.xtu.homework.entity.Homework;
import com.xtu.homework.entity.Submission;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 提交与评分服务单元测试
 */
@SpringBootTest(classes = HomeworkApplication.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SubmissionServiceImplTest {

    @Autowired
    private SubmissionService submissionService;
    @Autowired
    private HomeworkService homeworkService;

    private static Long testHomeworkId;

    @BeforeEach
    void setUp() {
        if (testHomeworkId == null) {
            HomeworkAssignDto dto = new HomeworkAssignDto();
            dto.setTitle("提交测试作业-" + System.currentTimeMillis());
            dto.setDescription("用于测试提交功能");
            dto.setTeachingClassId(1L);
            dto.setDeadline(LocalDateTime.now().plusDays(7));
            HomeworkAssignDto.QuestionItem qi1 = new HomeworkAssignDto.QuestionItem();
            qi1.setQuestionId(1L);
            qi1.setSortOrder(1);
            qi1.setScore(BigDecimal.valueOf(5));
            dto.setQuestions(List.of(qi1));
            Homework hw = homeworkService.assignHomework(2L, dto);
            testHomeworkId = hw.getId();
        }
    }

    @Test
    @Order(1)
    void testSubmitWithCorrectAnswer() {
        SubmissionDto dto = new SubmissionDto();
        dto.setHomeworkId(testHomeworkId);
        SubmissionDto.AnswerItem item = new SubmissionDto.AnswerItem();
        item.setQuestionId(1L);
        item.setAnswer("C"); // 正确答案
        dto.setAnswers(List.of(item));

        Submission sub = submissionService.submit(3L, dto);
        assertNotNull(sub.getId());
        assertEquals("SUBMITTED", sub.getStatus());
        assertEquals(0, BigDecimal.valueOf(5).compareTo(sub.getAutoScore()));
    }

    @Test
    @Order(2)
    void testSubmitWithWrongAnswer() {
        SubmissionDto dto = new SubmissionDto();
        dto.setHomeworkId(testHomeworkId);
        SubmissionDto.AnswerItem item = new SubmissionDto.AnswerItem();
        item.setQuestionId(1L);
        item.setAnswer("A"); // 错误答案
        dto.setAnswers(List.of(item));

        Submission sub = submissionService.submit(4L, dto);
        assertEquals(0, BigDecimal.ZERO.compareTo(sub.getAutoScore()));
    }

    @Test
    @Order(3)
    void testGetUngradedList() {
        List<Submission> list = submissionService.getUngradedList(testHomeworkId);
        assertNotNull(list);
        assertTrue(list.size() >= 2);
    }

    @Test
    @Order(4)
    void testModifyAnswer() {
        SubmissionDto dto = new SubmissionDto();
        dto.setHomeworkId(testHomeworkId);
        SubmissionDto.AnswerItem item = new SubmissionDto.AnswerItem();
        item.setQuestionId(1L);
        item.setAnswer("C"); // 改为正确答案
        dto.setAnswers(List.of(item));

        Submission sub = submissionService.modifyAnswer(3L, dto);
        assertEquals(0, BigDecimal.valueOf(5).compareTo(sub.getAutoScore()));
    }

    // ========== 评分列表（分页 + 过滤）==========

    @Test
    @Order(5)
    void testGradingPageAll() {
        Map<String, Object> r = submissionService.getGradingPage(testHomeworkId, 1, 10, "ALL");
        assertTrue(((Number) r.get("total")).intValue() >= 2, "应包含至少2条提交");
        List<?> records = (List<?>) r.get("records");
        assertEquals(((Number) r.get("total")).intValue(), records.size());
        // 每行应含学生姓名
        Map<?, ?> first = (Map<?, ?>) records.get(0);
        assertTrue(first.containsKey("studentName") && first.containsKey("status"));
    }

    @Test
    @Order(6)
    void testGradingPageFilterUngraded() {
        // 两道提交都是 SUBMITTED（未评分），UNGRADED 过滤应命中全部
        Map<String, Object> r = submissionService.getGradingPage(testHomeworkId, 1, 10, "UNGRADED");
        assertTrue(((Number) r.get("total")).intValue() >= 2);
        List<?> records = (List<?>) r.get("records");
        for (Object o : records) {
            assertEquals("SUBMITTED", ((Map<?, ?>) o).get("status"));
        }
    }

    @Test
    @Order(7)
    void testGradingPageSmallSize() {
        // size=1 应只返回 1 条（分页生效）
        Map<String, Object> r = submissionService.getGradingPage(testHomeworkId, 1, 1, "ALL");
        List<?> records = (List<?>) r.get("records");
        assertEquals(1, records.size(), "size=1 时每页只返回1条");
        assertTrue(((Number) r.get("total")).intValue() >= 2);
    }
}
