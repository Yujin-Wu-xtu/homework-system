package com.xtu.homework.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xtu.homework.common.R;
import com.xtu.homework.dao.SubmissionAnswerDao;
import com.xtu.homework.dao.SubmissionDao;
import com.xtu.homework.dto.SubmissionDto;
import com.xtu.homework.entity.Submission;
import com.xtu.homework.entity.SubmissionAnswer;
import com.xtu.homework.service.HomeworkService;
import com.xtu.homework.service.SubmissionService;
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

    @GetMapping("/homeworks")
    public R listHomeworks(@RequestAttribute("userId") Long studentId,
                           @RequestParam(defaultValue = "1") int page,
                           @RequestParam(defaultValue = "10") int size) {
        return R.ok().data(homeworkService.listStudentHomeworks(studentId, page, size));
    }

    @GetMapping("/homeworks/{id}")
    public R getHomeworkDetail(@PathVariable Long id,
                               @RequestAttribute("userId") Long studentId) {
        return R.ok().data(homeworkService.getHomeworkDetail(id, studentId));
    }

    @PostMapping("/homeworks/{id}/submit")
    public R submit(@PathVariable Long id,
                    @RequestAttribute("userId") Long studentId,
                    @Valid @RequestBody SubmissionDto dto) {
        dto.setHomeworkId(id);
        return R.ok().data(submissionService.submit(studentId, dto));
    }

    @GetMapping("/homeworks/{id}/result")
    public R getResult(@PathVariable Long id,
                       @RequestAttribute("userId") Long studentId) {
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

        Map<String, Object> result = new HashMap<>();
        result.put("submission", sub);
        result.put("answers", answers);
        return R.ok().data(result);
    }
}
