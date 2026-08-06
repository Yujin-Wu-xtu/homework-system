package com.xtu.homework.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xtu.homework.common.R;
import com.xtu.homework.dao.*;
import com.xtu.homework.entity.*;
import com.xtu.homework.service.QuestionService;
import com.xtu.homework.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserService userService;
    private final QuestionService questionService;
    private final UserDao userDao;
    private final ClazzDao clazzDao;
    private final QuestionDao questionDao;
    private final HomeworkDao homeworkDao;
    private final KnowledgePointDao knowledgePointDao;
    private final TeachingClassDao teachingClassDao;

    // ---- 首页统计 ----
    @GetMapping("/dashboard")
    public R dashboard() {
        long classCount = clazzDao.selectCount(null);
        long studentCount = userDao.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getRole, "STUDENT")
                        .eq(User::getStatus, "ACTIVE"));
        long teacherCount = userDao.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getRole, "TEACHER")
                        .eq(User::getStatus, "ACTIVE"));
        long questionCount = questionDao.selectCount(
                new LambdaQueryWrapper<Question>().eq(Question::getStatus, "ACTIVE"));
        long homeworkCount = homeworkDao.selectCount(null);
        long knowledgePointCount = knowledgePointDao.selectCount(null);
        return R.ok().data(Map.of(
                "classCount", classCount,
                "studentCount", studentCount,
                "teacherCount", teacherCount,
                "questionCount", questionCount,
                "homeworkCount", homeworkCount,
                "knowledgePointCount", knowledgePointCount));
    }

    // ---- 教师管理 ----
    @GetMapping("/teachers")
    public R listTeachers(@RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "10") int size,
                          @RequestParam(required = false) String keyword) {
        return R.ok().data(userService.listTeachers(page, size, keyword));
    }

    @PostMapping("/teachers")
    public R addTeacher(@RequestBody User teacher) {
        return R.ok().data(userService.addTeacher(teacher));
    }

    @PutMapping("/teachers/{id}")
    public R updateTeacher(@PathVariable Long id, @RequestBody User teacher) {
        teacher.setId(id);
        userService.updateById(teacher);
        return R.ok();
    }

    @DeleteMapping("/teachers/{id}")
    public R deleteTeacher(@PathVariable Long id) {
        User t = new User();
        t.setId(id);
        t.setStatus("DISABLED");
        userService.updateById(t);
        return R.ok();
    }

    @PutMapping("/teachers/{id}/reset-pwd")
    public R resetTeacherPwd(@PathVariable Long id) {
        return R.ok().data(Map.of("newPassword", userService.resetPassword(id)));
    }

    // ---- 班级管理 ----
    @GetMapping("/classes")
    public R listClasses(@RequestParam(defaultValue = "1") int page,
                         @RequestParam(defaultValue = "10") int size) {
        return R.ok().data(clazzDao.selectPage(new Page<>(page, size), null));
    }

    @PostMapping("/classes")
    public R addClass(@RequestBody Clazz clazz) {
        clazzDao.insert(clazz);
        return R.ok().data(clazz);
    }

    @PutMapping("/classes/{id}")
    public R updateClass(@PathVariable Long id, @RequestBody Clazz clazz) {
        clazz.setId(id);
        clazzDao.updateById(clazz);
        return R.ok();
    }

    @DeleteMapping("/classes/{id}")
    public R deleteClass(@PathVariable Long id) {
        long studentCount = userDao.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getClazzId, id)
                        .eq(User::getRole, "STUDENT"));
        if (studentCount > 0) {
            return R.badRequest("班级下有" + studentCount + "名学生，请先转移学生再删除");
        }
        clazzDao.deleteById(id);
        return R.ok();
    }

    @PostMapping("/classes/import")
    public R importClasses(@RequestParam("file") MultipartFile file) {
        try {
            List<Clazz> classes = userService.importClasses(file);
            return R.ok().data(Map.of("imported", classes.size(), "classes", classes));
        } catch (Exception e) {
            return R.badRequest("导入失败: " + e.getMessage());
        }
    }

    @GetMapping("/classes/{id}/students")
    public R listStudents(@PathVariable Long id,
                          @RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int size) {
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
        qw.eq(User::getClazzId, id).eq(User::getRole, "STUDENT").eq(User::getStatus, "ACTIVE");
        return R.ok().data(userDao.selectPage(new Page<>(page, size), qw));
    }

    @PutMapping("/classes/{id}/students/{sid}/transfer")
    public R transferStudent(@PathVariable Long id, @PathVariable Long sid,
                             @RequestBody Map<String, Long> body) {
        userService.transferStudent(sid, id, body.get("toClazzId"));
        return R.ok();
    }

    @PutMapping("/students/{id}/reset-pwd")
    public R resetStudentPwd(@PathVariable Long id) {
        return R.ok().data(Map.of("newPassword", userService.resetPassword(id)));
    }

    @PostMapping("/students/import")
    public R importStudents(@RequestParam("file") MultipartFile file,
                            @RequestParam Long clazzId) {
        try {
            return R.ok().data(userService.importStudentsFromExcel(clazzId, file));
        } catch (Exception e) {
            return R.badRequest("导入失败: " + e.getMessage());
        }
    }

    @PostMapping("/students")
    public R addStudent(@RequestBody Map<String, Object> body) {
        try {
            Long clazzId = ((Number) body.get("clazzId")).longValue();
            User s = new User();
            s.setUsername((String) body.get("username"));
            s.setRealName((String) body.get("realName"));
            s.setPhone((String) body.get("phone"));
            s.setEmail((String) body.get("email"));
            return R.ok().data(userService.addStudent(clazzId, s));
        } catch (RuntimeException e) {
            return R.badRequest(e.getMessage());
        }
    }

    @DeleteMapping("/students/{id}")
    public R deleteStudent(@PathVariable Long id) {
        try {
            userService.deleteStudent(id);
            return R.ok("学生已删除（账号禁用）");
        } catch (RuntimeException e) {
            return R.badRequest(e.getMessage());
        }
    }

    // ---- 题库管理 ----
    @GetMapping("/questions")
    public R listQuestions(@RequestParam(defaultValue = "1") int page,
                           @RequestParam(defaultValue = "10") int size,
                           @RequestParam(required = false) String keyword,
                           @RequestParam(required = false) String type,
                           @RequestParam(required = false) String difficulty) {
        return R.ok().data(questionService.searchQuestions(page, size, keyword, type, difficulty, null));
    }

    @PostMapping("/questions")
    public R addQuestion(@RequestBody Map<String, Object> body) {
        Question q = new Question();
        q.setType((String) body.get("type"));
        q.setContent((String) body.get("content"));
        q.setCorrectAnswer((String) body.get("correctAnswer"));
        q.setReferenceAnswer((String) body.get("referenceAnswer"));
        q.setScore(java.math.BigDecimal.valueOf(
                body.get("score") != null ? ((Number) body.get("score")).doubleValue() : 5.0));
        q.setDifficulty((String) body.getOrDefault("difficulty", "MEDIUM"));
        q.setCreatorId(1L); // admin

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> optList = (List<Map<String, Object>>) body.get("options");
        List<QuestionOption> options = null;
        if (optList != null && !optList.isEmpty()) {
            options = new java.util.ArrayList<>();
            for (Map<String, Object> o : optList) {
                QuestionOption opt = new QuestionOption();
                opt.setLabel((String) o.get("label"));
                opt.setContent((String) o.get("content"));
                opt.setSortOrder(((Number) o.getOrDefault("sortOrder", 0)).intValue());
                options.add(opt);
            }
        }

        @SuppressWarnings("unchecked")
        List<Long> kpIds = (List<Long>) body.get("knowledgePointIds");
        return R.ok().data(questionService.addQuestion(q, options, kpIds));
    }

    @PutMapping("/questions/{id}")
    public R updateQuestion(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Question q = questionDao.selectById(id);
        if (q == null) return R.badRequest("题目不存在");
        if (body.containsKey("content")) q.setContent((String) body.get("content"));
        if (body.containsKey("correctAnswer")) q.setCorrectAnswer((String) body.get("correctAnswer"));
        if (body.containsKey("referenceAnswer")) q.setReferenceAnswer((String) body.get("referenceAnswer"));
        if (body.containsKey("difficulty")) q.setDifficulty((String) body.get("difficulty"));
        if (body.containsKey("score"))
            q.setScore(java.math.BigDecimal.valueOf(((Number) body.get("score")).doubleValue()));
        questionDao.updateById(q);
        return R.ok().data(q);
    }

    @DeleteMapping("/questions/{id}")
    public R deleteQuestion(@PathVariable Long id) {
        questionDao.deleteById(id);
        return R.ok();
    }

    @GetMapping("/questions/{id}/options")
    public R getQuestionOptions(@PathVariable Long id) {
        return R.ok().data(questionService.getOptions(id));
    }

    @PostMapping("/questions/check-duplicate")
    public R checkDuplicate(@RequestBody Map<String, String> body) {
        return R.ok().data(questionService.checkDuplicate(
                body.get("content"), body.get("type")));
    }

    @PostMapping("/questions/import")
    public R importQuestions(@RequestParam("file") MultipartFile file) {
        try {
            return R.ok().data(questionService.importQuestionsFromExcel(file));
        } catch (Exception e) {
            return R.badRequest("导入失败: " + e.getMessage());
        }
    }

    @PutMapping("/questions/{id}/status")
    public R toggleStatus(@PathVariable Long id) {
        questionService.toggleStatus(id);
        return R.ok();
    }

    // ---- 知识点管理 ----
    @GetMapping("/knowledge-points")
    public R listKnowledgePoints(@RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<KnowledgePoint> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.like(KnowledgePoint::getName, keyword);
        }
        qw.orderByAsc(KnowledgePoint::getSubject, KnowledgePoint::getName);
        return R.ok().data(knowledgePointDao.selectList(qw));
    }

    @PostMapping("/knowledge-points")
    public R addKnowledgePoint(@RequestBody KnowledgePoint kp) {
        knowledgePointDao.insert(kp);
        return R.ok().data(kp);
    }

    @PutMapping("/knowledge-points/{id}")
    public R updateKnowledgePoint(@PathVariable Long id, @RequestBody KnowledgePoint kp) {
        kp.setId(id);
        knowledgePointDao.updateById(kp);
        return R.ok();
    }

    @DeleteMapping("/knowledge-points/{id}")
    public R deleteKnowledgePoint(@PathVariable Long id) {
        knowledgePointDao.deleteById(id);
        return R.ok();
    }

    // ---- 教学班管理 ----
    @GetMapping("/teaching-classes")
    public R listTeachingClasses(@RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "10") int size) {
        return R.ok().data(teachingClassDao.selectPage(new Page<>(page, size), null));
    }

    @PostMapping("/teaching-classes")
    public R addTeachingClass(@RequestBody TeachingClass tc) {
        teachingClassDao.insert(tc);
        return R.ok().data(tc);
    }

    @DeleteMapping("/teaching-classes/{id}")
    public R deleteTeachingClass(@PathVariable Long id) {
        teachingClassDao.deleteById(id);
        return R.ok();
    }
}
