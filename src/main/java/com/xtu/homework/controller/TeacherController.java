package com.xtu.homework.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xtu.homework.common.R;
import com.xtu.homework.dao.ClazzDao;
import com.xtu.homework.dao.HomeworkDao;
import com.xtu.homework.dao.HomeworkQuestionDao;
import com.xtu.homework.dao.QuestionDao;
import com.xtu.homework.dao.SubmissionAnswerDao;
import com.xtu.homework.dao.SubmissionDao;
import com.xtu.homework.dao.TeachingClassClazzDao;
import com.xtu.homework.dao.TeachingClassDao;
import com.xtu.homework.dao.UserDao;
import com.xtu.homework.dto.GradingDto;
import com.xtu.homework.dto.HomeworkAssignDto;
import com.xtu.homework.entity.*;
import com.xtu.homework.service.HomeworkService;
import com.xtu.homework.service.QuestionService;
import com.xtu.homework.service.SubmissionService;
import com.xtu.homework.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/teacher")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TEACHER')")
public class TeacherController {

    private final HomeworkService homeworkService;
    private final SubmissionService submissionService;
    private final QuestionService questionService;
    private final UserService userService;
    private final HomeworkDao homeworkDao;
    private final HomeworkQuestionDao homeworkQuestionDao;
    private final SubmissionDao submissionDao;
    private final SubmissionAnswerDao submissionAnswerDao;
    private final QuestionDao questionDao;
    private final TeachingClassDao teachingClassDao;
    private final TeachingClassClazzDao teachingClassClazzDao;
    private final ClazzDao clazzDao;
    private final UserDao userDao;

    // ---- 教学班级管理（需求：利用自然班级组建教学班、增删改查、重置教学班学生密码）----

    @GetMapping("/classes")
    public R listClasses(@RequestParam(defaultValue = "1") int page,
                         @RequestParam(defaultValue = "100") int size) {
        // 教师组建教学班需查看自然班级（只读）
        return R.ok().data(clazzDao.selectPage(new Page<>(page, size), null));
    }

    @GetMapping("/teaching-classes")
    public R listTeachingClasses(@RequestAttribute("userId") Long teacherId,
                                 @RequestParam(defaultValue = "1") int page,
                                 @RequestParam(defaultValue = "10") int size) {
        return R.ok().data(teachingClassDao.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<TeachingClass>()
                        .eq(TeachingClass::getTeacherId, teacherId)
                        .orderByDesc(TeachingClass::getCreateTime)));
    }

    @PostMapping("/teaching-classes")
    public R addTeachingClass(@RequestAttribute("userId") Long teacherId,
                              @RequestBody TeachingClass tc) {
        if (tc.getName() == null || tc.getName().isBlank()) {
            return R.badRequest("教学班名称不能为空");
        }
        tc.setTeacherId(teacherId);
        teachingClassDao.insert(tc);
        return R.ok().data(tc);
    }

    @PutMapping("/teaching-classes/{id}")
    public R updateTeachingClass(@RequestAttribute("userId") Long teacherId,
                                 @PathVariable Long id, @RequestBody TeachingClass tc) {
        TeachingClass exist = teachingClassDao.selectById(id);
        if (exist == null || !exist.getTeacherId().equals(teacherId)) {
            return R.badRequest("教学班不存在或无权操作");
        }
        if (tc.getName() != null && !tc.getName().isBlank()) {
            exist.setName(tc.getName());
        }
        teachingClassDao.updateById(exist);
        return R.ok().data(exist);
    }

    @DeleteMapping("/teaching-classes/{id}")
    public R deleteTeachingClass(@RequestAttribute("userId") Long teacherId,
                                 @PathVariable Long id) {
        TeachingClass exist = teachingClassDao.selectById(id);
        if (exist == null || !exist.getTeacherId().equals(teacherId)) {
            return R.badRequest("教学班不存在或无权操作");
        }
        // 先删除关联，再删教学班（外键约束）
        teachingClassClazzDao.delete(new LambdaQueryWrapper<TeachingClassClazz>()
                .eq(TeachingClassClazz::getTeachingClassId, id));
        teachingClassDao.deleteById(id);
        return R.ok("教学班已删除");
    }

    @GetMapping("/teaching-classes/{id}")
    public R getTeachingClassDetail(@RequestAttribute("userId") Long teacherId,
                                    @PathVariable Long id) {
        TeachingClass exist = teachingClassDao.selectById(id);
        if (exist == null || !exist.getTeacherId().equals(teacherId)) {
            return R.badRequest("教学班不存在或无权操作");
        }
        List<Clazz> classes = clazzDao.selectList(new LambdaQueryWrapper<Clazz>()
                .inSql(Clazz::getId, "SELECT clazz_id FROM teaching_class_clazz WHERE teaching_class_id = " + id));
        return R.ok().data(Map.of("teachingClass", exist, "classes", classes));
    }

    @PostMapping("/teaching-classes/{id}/classes")
    public R addClassesToTeachingClass(@RequestAttribute("userId") Long teacherId,
                                       @PathVariable Long id,
                                       @RequestBody Map<String, Object> body) {
        TeachingClass exist = teachingClassDao.selectById(id);
        if (exist == null || !exist.getTeacherId().equals(teacherId)) {
            return R.badRequest("教学班不存在或无权操作");
        }
        // 泛型擦除陷阱：JSON 数字默认解析为 Integer，需经 Number 转 Long
        @SuppressWarnings("unchecked")
        List<?> rawIds = body.get("clazzIds") instanceof List
                ? (List<?>) body.get("clazzIds") : List.of();
        int added = 0;
        for (Object o : rawIds) {
            Long cid = ((Number) o).longValue();
            Long cnt = teachingClassClazzDao.selectCount(new LambdaQueryWrapper<TeachingClassClazz>()
                    .eq(TeachingClassClazz::getTeachingClassId, id)
                    .eq(TeachingClassClazz::getClazzId, cid));
            if (cnt == 0) {
                TeachingClassClazz tcc = new TeachingClassClazz();
                tcc.setTeachingClassId(id);
                tcc.setClazzId(cid);
                teachingClassClazzDao.insert(tcc);
                added++;
            }
        }
        return R.ok().data(Map.of("added", added));
    }

    @DeleteMapping("/teaching-classes/{id}/classes/{clazzId}")
    public R removeClassFromTeachingClass(@RequestAttribute("userId") Long teacherId,
                                          @PathVariable Long id, @PathVariable Long clazzId) {
        TeachingClass exist = teachingClassDao.selectById(id);
        if (exist == null || !exist.getTeacherId().equals(teacherId)) {
            return R.badRequest("教学班不存在或无权操作");
        }
        teachingClassClazzDao.delete(new LambdaQueryWrapper<TeachingClassClazz>()
                .eq(TeachingClassClazz::getTeachingClassId, id)
                .eq(TeachingClassClazz::getClazzId, clazzId));
        return R.ok("已从教学班移除该班级");
    }

    @GetMapping("/teaching-classes/{id}/students")
    public R listTeachingClassStudents(@RequestAttribute("userId") Long teacherId,
                                       @PathVariable Long id) {
        TeachingClass exist = teachingClassDao.selectById(id);
        if (exist == null || !exist.getTeacherId().equals(teacherId)) {
            return R.badRequest("教学班不存在或无权操作");
        }
        return R.ok().data(userDao.findStudentsByTeachingClassId(id));
    }

    @PutMapping("/teaching-classes/{id}/reset-student-pwds")
    public R resetTeachingClassStudentPwds(@RequestAttribute("userId") Long teacherId,
                                           @PathVariable Long id) {
        TeachingClass exist = teachingClassDao.selectById(id);
        if (exist == null || !exist.getTeacherId().equals(teacherId)) {
            return R.badRequest("教学班不存在或无权操作");
        }
        List<User> students = userDao.findStudentsByTeachingClassId(id);
        List<Map<String, String>> resets = new ArrayList<>();
        for (User s : students) {
            String newPwd = userService.resetPassword(s.getId());
            resets.add(Map.of("username", s.getUsername(), "realName", s.getRealName(),
                    "newPassword", newPwd));
        }
        return R.ok().data(Map.of("resetCount", resets.size(), "students", resets));
    }

    // ---- 作业管理 ----
    @GetMapping("/homeworks")
    public R listHomeworks(@RequestAttribute("userId") Long teacherId,
                           @RequestParam(defaultValue = "1") int page,
                           @RequestParam(defaultValue = "10") int size) {
        LambdaQueryWrapper<Homework> qw = new LambdaQueryWrapper<>();
        qw.eq(Homework::getTeacherId, teacherId).orderByDesc(Homework::getCreateTime);
        Page<Homework> result = homeworkDao.selectPage(new Page<>(page, size), qw);
        return R.ok().data(result);
    }

    @PostMapping("/homeworks")
    public R assignHomework(@RequestAttribute("userId") Long teacherId,
                            @Valid @RequestBody HomeworkAssignDto dto) {
        return R.ok().data(homeworkService.assignHomework(teacherId, dto));
    }

    @PutMapping("/homeworks/{id}")
    public R updateHomework(@PathVariable Long id,
                            @Valid @RequestBody HomeworkAssignDto dto) {
        return R.ok().data(homeworkService.updateHomework(id, dto));
    }

    @DeleteMapping("/homeworks/{id}")
    public R deleteHomework(@PathVariable Long id) {
        homeworkService.deleteHomework(id);
        return R.ok();
    }

    @GetMapping("/homeworks/{id}/detail")
    public R getHomeworkDetail(@PathVariable Long id) {
        Homework hw = homeworkDao.selectById(id);
        if (hw == null) return R.badRequest("作业不存在");

        List<HomeworkQuestion> hqList = homeworkQuestionDao.selectList(
                new LambdaQueryWrapper<HomeworkQuestion>()
                        .eq(HomeworkQuestion::getHomeworkId, id)
                        .orderByAsc(HomeworkQuestion::getSortOrder));

        List<Map<String, Object>> questions = new ArrayList<>();
        for (HomeworkQuestion hq : hqList) {
            Map<String, Object> qm = new HashMap<>();
            qm.put("homeworkQuestionId", hq.getId());
            qm.put("questionId", hq.getQuestionId());
            qm.put("score", hq.getScore());
            qm.put("sortOrder", hq.getSortOrder());
            questions.add(qm);
        }

        return R.ok().data(Map.of("homework", hw, "questions", questions));
    }

    @GetMapping("/homeworks/{id}/submissions")
    public R getSubmissions(@PathVariable Long id) {
        List<Map<String, Object>> status = homeworkService.getSubmissionStatus(id);

        // 为每个学生附加答案详情
        List<Submission> subs = submissionDao.selectList(
                new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getHomeworkId, id));
        for (Map<String, Object> s : status) {
            Long studentId = (Long) s.get("studentId");
            Submission match = subs.stream()
                    .filter(sub -> sub.getStudentId().equals(studentId))
                    .findFirst().orElse(null);
            if (match != null) {
                s.put("submissionId", match.getId());
                s.put("durationSeconds", match.getDurationSeconds());
                s.put("suspiciousFlag", match.getSuspiciousFlag());
            }
        }
        return R.ok().data(status);
    }

    @GetMapping("/homeworks/{id}/submissions/{subId}/answers")
    public R getSubmissionAnswers(@PathVariable Long subId) {
        List<SubmissionAnswer> answers = submissionAnswerDao.selectList(
                new LambdaQueryWrapper<SubmissionAnswer>()
                        .eq(SubmissionAnswer::getSubmissionId, subId));
        // 填充 questionType 供前端显示
        for (SubmissionAnswer a : answers) {
            Question q = questionDao.selectById(a.getQuestionId());
            if (q != null) a.setQuestionType(q.getType());
        }
        return R.ok().data(answers);
    }

    @GetMapping("/homeworks/{id}/grading")
    public R getGradingList(@PathVariable Long id) {
        return R.ok().data(submissionService.getUngradedList(id));
    }

    @PutMapping("/homeworks/{hwId}/grading/{answerId}")
    public R gradeAnswer(@PathVariable Long answerId,
                         @RequestAttribute("userId") Long teacherId,
                         @Valid @RequestBody GradingDto dto) {
        submissionService.gradeAnswer(answerId, teacherId, dto.getScore(), dto.getComment());
        return R.ok("评分已保存");
    }

    @GetMapping("/homeworks/{id}/export")
    public ResponseEntity<byte[]> exportGrades(@PathVariable Long id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=grades.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(homeworkService.exportGrades(id));
    }

    // ---- 题库查询 ----
    @GetMapping("/questions/search")
    public R searchQuestions(@RequestParam(defaultValue = "1") int page,
                             @RequestParam(defaultValue = "10") int size,
                             @RequestParam(required = false) String keyword,
                             @RequestParam(required = false) String type,
                             @RequestParam(required = false) String difficulty,
                             @RequestParam(required = false) Long knowledgePointId) {
        return R.ok().data(questionService.searchQuestions(
                page, size, keyword, type, difficulty, knowledgePointId));
    }
}
