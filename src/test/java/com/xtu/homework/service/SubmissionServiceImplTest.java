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
}
