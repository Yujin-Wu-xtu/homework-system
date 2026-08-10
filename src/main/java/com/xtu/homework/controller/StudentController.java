package com.xtu.homework.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xtu.homework.common.R;
import com.xtu.homework.dao.SubmissionAnswerDao;
import com.xtu.homework.dao.SubmissionDao;
import com.xtu.homework.dao.QuestionDao;
import com.xtu.homework.dto.SubmissionDto;
import com.xtu.homework.entity.Submission;
import com.xtu.homework.entity.SubmissionAnswer;
import com.xtu.homework.entity.Question;
import com.xtu.homework.service.HomeworkService;
import com.xtu.homework.service.SubmissionService;
import com.xtu.homework.service.StudentHomeworkAccessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class StudentController {

    private final HomeworkService homeworkService;
    private final SubmissionService submissionService;
    private final SubmissionDao submissionDao;
    private final SubmissionAnswerDao submissionAnswerDao;
    private final QuestionDao questionDao;
    private final StudentHomeworkAccessService studentHomeworkAccessService;

    @GetMapping("/homeworks")
    public R listHomeworks(@RequestAttribute("userId") Long studentId,
                           @RequestParam(defaultValue = "1") int page,
                           @RequestParam(defaultValue = "10") int size) {
        return R.ok().data(homeworkService.listStudentHomeworks(studentId, page, size));
    }

    @GetMapping("/homeworks/{id}")
    public R getHomeworkDetail(@PathVariable Long id,
                               @RequestAttribute("userId") Long studentId) {
        try {
            return R.ok().data(homeworkService.getHomeworkDetail(id, studentId));
        } catch (RuntimeException e) {
            return R.badRequest(e.getMessage());
        }
    }

    @PostMapping("/homeworks/{id}/submit")
    public R submit(@PathVariable Long id,
                    @RequestAttribute("userId") Long studentId,
                    @Valid @RequestBody SubmissionDto dto) {
        try {
            dto.setHomeworkId(id);
            return R.ok().data(submissionService.submit(studentId, dto));
        } catch (RuntimeException e) {
            return R.badRequest(e.getMessage());
        }
    }

    @GetMapping("/homeworks/{id}/result")
    public R getResult(@PathVariable Long id,
                       @RequestAttribute("userId") Long studentId) {
        if (!studentHomeworkAccessService.canAccess(id, studentId)) {
            return R.badRequest("该作业不属于当前教学班，无法访问");
        }
        Submission sub = submissionDao.selectOne(
                new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getHomeworkId, id)
                        .eq(Submission::getStudentId, studentId));
        if (sub == null) return R.badRequest("未找到提交记录");
        if (!"GRADED".equals(sub.getStatus()) && !"SUBMITTED".equals(sub.getStatus())) {
            return R.badRequest("作业尚未提交");
        }

        List<SubmissionAnswer> answers = submissionAnswerDao.selectList(
                new LambdaQueryWrapper<SubmissionAnswer>()
                        .eq(SubmissionAnswer::getSubmissionId, sub.getId()));
        // 填充题型供前端区分渲染（应用题富文本答案 vs 问答题纯文本，与教师端 getSubmissionAnswers 同模式）
        for (SubmissionAnswer a : answers) {
            Question q = questionDao.selectById(a.getQuestionId());
            if (q != null) a.setQuestionType(q.getType());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("submission", sub);
        result.put("answers", answers);
        return R.ok().data(result);
    }
}
