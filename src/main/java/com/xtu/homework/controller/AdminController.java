package com.xtu.homework.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xtu.homework.common.R;
import com.xtu.homework.dao.*;
import com.xtu.homework.entity.*;
import com.xtu.homework.service.QuestionService;
import com.xtu.homework.service.QuestionAiService;
import com.xtu.homework.service.UserService;
import com.xtu.homework.util.TextExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

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
    private final QuestionKnowledgeDao questionKnowledgeDao;
    private final QuestionOptionDao questionOptionDao;
    private final HomeworkQuestionDao homeworkQuestionDao;
    private final SubmissionDao submissionDao;
    private final QuestionAiService questionAiService;
    private final AiMaterialDao aiMaterialDao;
    private final QuestionImageDao questionImageDao;

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
        long teachingClassCount = teachingClassDao.selectCount(null);
        // 近 7 天提交趋势（含今天；按 submitTime 实际提交时刻 Java 侧聚合，避免 DATE() 函数方言差异）
        LocalDate today = LocalDate.now();
        List<Submission> recentSubs = submissionDao.selectList(new LambdaQueryWrapper<Submission>()
                .isNotNull(Submission::getSubmitTime)
                .ge(Submission::getSubmitTime, today.minusDays(6).atStartOfDay()));
        Map<String, Long> trendMap = recentSubs.stream()
                .collect(Collectors.groupingBy(s -> s.getSubmitTime().toLocalDate().toString(), Collectors.counting()));
        List<Map<String, Object>> submitTrend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            String day = today.minusDays(i).toString();
            submitTrend.add(Map.of("date", day, "count", trendMap.getOrDefault(day, 0L)));
        }
        // 题型分布（Java 侧分组）
        List<Question> activeQuestions = questionDao.selectList(
                new LambdaQueryWrapper<Question>().eq(Question::getStatus, "ACTIVE"));
        Map<String, Long> typeMap = activeQuestions.stream()
                .collect(Collectors.groupingBy(q -> q.getType() == null ? "UNKNOWN" : q.getType(), Collectors.counting()));
        List<Map<String, Object>> questionTypeDist = new ArrayList<>();
        typeMap.forEach((t, c) -> {
            Map<String, Object> m = new HashMap<>();
            m.put("type", t);
            m.put("count", c);
            questionTypeDist.add(m);
        });
        // 最近 5 个作业 + 提交统计（submitted=已提交人数 / total=应提交人数）
        List<Homework> recent = homeworkDao.selectList(new QueryWrapper<Homework>()
                .orderByDesc("create_time").last("LIMIT 5"));
        List<Submission> allSubs = submissionDao.selectList(null);
        Map<Long, Long> totalByHw = allSubs.stream().collect(Collectors.groupingBy(
                Submission::getHomeworkId, Collectors.counting()));
        Map<Long, Long> submittedByHw = allSubs.stream()
                .filter(s -> !"NOT_SUBMITTED".equals(s.getStatus()))
                .collect(Collectors.groupingBy(Submission::getHomeworkId, Collectors.counting()));
        List<Map<String, Object>> recentHomeworks = new ArrayList<>();
        for (Homework h : recent) {
            TeachingClass tc = teachingClassDao.selectById(h.getTeachingClassId());
            long total = totalByHw.getOrDefault(h.getId(), 0L);
            long submitted = submittedByHw.getOrDefault(h.getId(), 0L);
            recentHomeworks.add(Map.of(
                    "id", h.getId(), "title", h.getTitle(),
                    "teachingClassName", tc == null ? "—" : tc.getName(),
                    "deadline", String.valueOf(h.getDeadline()),
                    "status", h.getStatus(),
                    "submitted", submitted, "total", total));
        }
        return R.ok().data(Map.of(
                "classCount", classCount,
                "studentCount", studentCount,
                "teacherCount", teacherCount,
                "questionCount", questionCount,
                "homeworkCount", homeworkCount,
                "teachingClassCount", teachingClassCount,
                "submitTrend", submitTrend,
                "questionTypeDist", questionTypeDist,
                "recentHomeworks", recentHomeworks));
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
        try {
            return R.ok().data(userService.addTeacher(teacher));
        } catch (RuntimeException e) {
            return R.badRequest(e.getMessage());
        }
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

    /** 班级层级树：学院 → 专业 → 班级（节点带学生数；老数据学院为空归"未分类学院"）——管理员班级管理左树右表数据源 */
    @GetMapping("/classes/tree")
    public R getClassTree() {
        List<Clazz> classes = clazzDao.selectList(new LambdaQueryWrapper<Clazz>()
                .orderByAsc(Clazz::getCollege).orderByAsc(Clazz::getMajor).orderByAsc(Clazz::getName));
        // 学生数按班级分组
        List<User> students = userDao.selectList(new LambdaQueryWrapper<User>().eq(User::getRole, "STUDENT"));
        Map<Long, Long> cntByClazz = students.stream().collect(Collectors.groupingBy(
                s -> s.getClazzId() == null ? -1L : s.getClazzId(), Collectors.counting()));
        // 三级聚合：学院 → 专业 → 班级
        Map<String, Map<String, List<Map<String, Object>>>> tree = new LinkedHashMap<>();
        for (Clazz c : classes) {
            String college = (c.getCollege() == null || c.getCollege().isBlank()) ? "未分类学院" : c.getCollege();
            String major = (c.getMajor() == null || c.getMajor().isBlank()) ? "未分类专业" : c.getMajor();
            tree.computeIfAbsent(college, k -> new LinkedHashMap<>())
                    .computeIfAbsent(major, k -> new ArrayList<>())
                    .add(Map.of("label", c.getName(), "clazzId", c.getId(), "name", c.getName(), "grade", c.getGrade(),
                            "college", c.getCollege() == null ? "" : c.getCollege(),
                            "major", c.getMajor() == null ? "" : c.getMajor(),
                            "studentCount", cntByClazz.getOrDefault(c.getId(), 0L)));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        tree.forEach((college, majors) -> {
            List<Map<String, Object>> majorNodes = new ArrayList<>();
            majors.forEach((major, clazzes) -> {
                long majorCount = clazzes.stream().mapToLong(m -> (Long) m.get("studentCount")).sum();
                majorNodes.add(Map.of("label", major, "studentCount", majorCount, "children", clazzes));
            });
            long collegeCount = majorNodes.stream().mapToLong(m -> (Long) m.get("studentCount")).sum();
            result.add(Map.of("label", college, "studentCount", collegeCount, "children", majorNodes));
        });
        return R.ok().data(result);
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
        if (id == null) {
            return R.badRequest("班级不存在");
        }
        // 只统计在册（ACTIVE）学生：软删遗留的 DISABLED 账号不阻止删除班级
        long activeCount = userDao.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getClazzId, id)
                        .eq(User::getRole, "STUDENT")
                        .eq(User::getStatus, "ACTIVE"));
        if (activeCount > 0) {
            return R.badRequest("班级下有 " + activeCount + " 名在册学生，请先转移学生再删除");
        }
        // 禁用（软删遗留）账号解除班级归属，避免孤儿引用
        userDao.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getClazzId, id)
                .eq(User::getRole, "STUDENT")
                .set(User::getClazzId, null));
        clazzDao.deleteById(id);
        return R.ok("班级已删除");
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

    @GetMapping("/students")
    public R listAllStudents(@RequestParam(defaultValue = "1") int page,
                             @RequestParam(defaultValue = "50") int size,
                             @RequestParam(required = false) String keyword,
                             @RequestParam(required = false) Long clazzId) {
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
        qw.eq(User::getRole, "STUDENT");
        if (clazzId != null) qw.eq(User::getClazzId, clazzId);
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(User::getUsername, keyword)
                          .or().like(User::getRealName, keyword));
        }
        qw.orderByAsc(User::getClazzId).orderByAsc(User::getUsername);
        return R.ok().data(userDao.selectPage(new Page<>(page, size), qw));
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
            return R.ok("学生已删除（账号及提交记录已一并清理）");
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
        List<Number> kpNums = (List<Number>) body.get("knowledgePointIds");
        List<Long> kpIds = null;
        if (kpNums != null) {
            kpIds = kpNums.stream().map(Number::longValue).toList();
        }
        try {
            return R.ok().data(questionService.addQuestion(q, options, kpIds));
        } catch (RuntimeException e) {
            return R.badRequest(e.getMessage());
        }
    }

    @PutMapping("/questions/{id}")
    public R updateQuestion(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Question q = new Question();
        if (body.containsKey("content")) q.setContent((String) body.get("content"));
        if (body.containsKey("correctAnswer")) q.setCorrectAnswer((String) body.get("correctAnswer"));
        if (body.containsKey("referenceAnswer")) q.setReferenceAnswer((String) body.get("referenceAnswer"));
        if (body.containsKey("difficulty")) q.setDifficulty((String) body.get("difficulty"));
        if (body.containsKey("score"))
            q.setScore(java.math.BigDecimal.valueOf(((Number) body.get("score")).doubleValue()));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> optList = (List<Map<String, Object>>) body.get("options");
        List<QuestionOption> options = null;
        if (optList != null) {
            options = new java.util.ArrayList<>();
            for (Map<String, Object> o : optList) {
                QuestionOption opt = new QuestionOption();
                opt.setLabel((String) o.get("label"));
                opt.setContent((String) o.get("content"));
                options.add(opt);
            }
        }

        @SuppressWarnings("unchecked")
        List<Number> kpNums = (List<Number>) body.get("knowledgePointIds");
        List<Long> kpIds = null;
        if (kpNums != null) {
            kpIds = kpNums.stream().map(Number::longValue).toList();
        }
        try {
            return R.ok().data(questionService.updateQuestion(id, q, options, kpIds));
        } catch (RuntimeException e) {
            return R.badRequest(e.getMessage());
        }
    }

    @GetMapping("/questions/{id}/knowledge-points")
    public R getQuestionKnowledgePoints(@PathVariable Long id) {
        List<Long> kpIds = questionKnowledgeDao.selectList(
                        new LambdaQueryWrapper<QuestionKnowledge>()
                                .eq(QuestionKnowledge::getQuestionId, id))
                .stream().map(QuestionKnowledge::getKnowledgePointId).toList();
        return R.ok().data(kpIds);
    }

    @DeleteMapping("/questions/{id}")
    public R deleteQuestion(@PathVariable Long id) {
        // 被作业引用时拒绝删除（可改为禁用）
        Long used = homeworkQuestionDao.selectCount(
                new LambdaQueryWrapper<HomeworkQuestion>().eq(HomeworkQuestion::getQuestionId, id));
        if (used > 0) {
            return R.badRequest("该题目已被作业引用，无法删除（可改为禁用）");
        }
        // 先删选项与知识点关联，再删题（外键约束）
        questionOptionDao.delete(new LambdaQueryWrapper<QuestionOption>()
                .eq(QuestionOption::getQuestionId, id));
        questionKnowledgeDao.delete(new LambdaQueryWrapper<QuestionKnowledge>()
                .eq(QuestionKnowledge::getQuestionId, id));
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

    // ---- AI 出题（大模型生成题目草稿，管理员预览审核后走常规新增入库）----
    // 资源文件存储目录（data/ 已在 .gitignore，不入版本库）；用 user.dir 拼绝对路径，避免相对路径解析到 Tomcat 临时目录
    private static final String AI_MATERIAL_DIR =
            System.getProperty("user.dir") + java.io.File.separator + "data" + java.io.File.separator + "ai-materials" + java.io.File.separator;

    @PostMapping("/materials")
    public R uploadMaterial(@RequestParam("file") MultipartFile file,
                            @RequestAttribute("userId") Long userId) {
        try {
            String name = file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename();
            String lower = name.toLowerCase();
            String type;
            if (lower.endsWith(".pdf")) type = "pdf";
            else if (lower.endsWith(".docx")) type = "docx";
            else if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown")) type = "txt";
            else throw new RuntimeException("不支持的文件类型: " + name + "（支持 pdf/docx/txt/md）");

            // 存储：data/ai-materials/<uuid>.<ext>，避免中文名/重名问题
            String ext = lower.substring(lower.lastIndexOf('.'));
            String storedName = java.util.UUID.randomUUID().toString().replace("-", "") + ext;
            java.io.File dir = new java.io.File(AI_MATERIAL_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new RuntimeException("资源目录创建失败");
            }
            java.io.File target = new java.io.File(dir, storedName);
            file.transferTo(target);

            AiMaterial m = new AiMaterial();
            m.setFileName(name);
            // 存相对路径（data/ai-materials/xxx.pdf）：读取时按 user.dir 解析，Windows/WSL/Docker 均可用
            m.setFilePath("data/ai-materials/" + storedName);
            m.setFileSize(file.getSize());
            m.setFileType(type);
            m.setUploaderId(userId);
            aiMaterialDao.insert(m);
            return R.ok("上传成功").data(m);
        } catch (RuntimeException e) {
            return R.badRequest(e.getMessage());
        } catch (Exception e) {
            return R.badRequest("上传失败: " + e.getMessage());
        }
    }

    @GetMapping("/materials")
    public R listMaterials() {
        return R.ok().data(aiMaterialDao.selectList(
                new LambdaQueryWrapper<AiMaterial>().orderByDesc(AiMaterial::getCreateTime)));
    }

    @DeleteMapping("/materials/{id}")
    public R deleteMaterial(@PathVariable Long id) {
        AiMaterial m = aiMaterialDao.selectById(id);
        if (m == null) return R.badRequest("资源不存在");
        try {
            java.io.File f = new java.io.File(m.getFilePath()).getAbsoluteFile();
            if (f.exists() && !f.delete()) {
                // 文件删除失败不阻断（记录删除即可）
            }
        } catch (Exception ignored) {
        }
        aiMaterialDao.deleteById(id);
        return R.ok("已删除");
    }

    // ---- 题干图片（应用题富文本插图：上传后返回 URL 供编辑器插入 <img src>）----
    private static final String QUESTION_IMAGE_DIR =
            System.getProperty("user.dir") + java.io.File.separator + "data" + java.io.File.separator
                    + "question-images" + java.io.File.separator;

    @PostMapping("/question-images")
    public R uploadQuestionImage(@RequestParam("file") MultipartFile file,
                                 @RequestAttribute("userId") Long userId) {
        try {
            String name = file.getOriginalFilename() == null ? "unnamed.png" : file.getOriginalFilename();
            String lower = name.toLowerCase();
            String type;
            if (lower.endsWith(".png")) type = "png";
            else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) type = "jpg";
            else if (lower.endsWith(".gif")) type = "gif";
            else if (lower.endsWith(".webp")) type = "webp";
            else throw new RuntimeException("不支持的图片类型: " + name + "（支持 png/jpg/jpeg/gif/webp）");

            // 存储：data/question-images/<uuid>.<ext>，避免中文名/重名；浏览器经 /question-images/xxx 访问
            String ext = lower.substring(lower.lastIndexOf('.'));
            String storedName = java.util.UUID.randomUUID().toString().replace("-", "") + ext;
            java.io.File dir = new java.io.File(QUESTION_IMAGE_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new RuntimeException("图片目录创建失败");
            }
            java.io.File target = new java.io.File(dir, storedName);
            file.transferTo(target);

            QuestionImage img = new QuestionImage();
            img.setFileName(name);
            img.setFilePath("data/question-images/" + storedName);
            img.setFileSize(file.getSize());
            img.setFileType(type);
            img.setUploaderId(userId);
            questionImageDao.insert(img);
            return R.ok("上传成功").data(Map.of(
                    "id", img.getId(),
                    "fileName", name,
                    "url", "/question-images/" + storedName));
        } catch (RuntimeException e) {
            return R.badRequest(e.getMessage());
        } catch (Exception e) {
            return R.badRequest("上传失败: " + e.getMessage());
        }
    }

    @GetMapping("/question-images")
    public R listQuestionImages() {
        return R.ok().data(questionImageDao.selectList(
                new LambdaQueryWrapper<QuestionImage>().orderByDesc(QuestionImage::getCreateTime)));
    }

    @DeleteMapping("/question-images/{id}")
    public R deleteQuestionImage(@PathVariable Long id) {
        QuestionImage img = questionImageDao.selectById(id);
        if (img == null) return R.badRequest("图片不存在");
        // 被题目题干引用时拒绝删除（防止题干图片裂图）
        String storedName = img.getFilePath().substring(img.getFilePath().lastIndexOf('/') + 1);
        Long refCount = questionDao.selectCount(
                new LambdaQueryWrapper<Question>().like(Question::getContent, "/question-images/" + storedName));
        if (refCount != null && refCount > 0) {
            return R.badRequest("该图片正被 " + refCount + " 道题目引用，请先编辑题目移除图片");
        }
        try {
            java.io.File f = new java.io.File(img.getFilePath()).getAbsoluteFile();
            if (f.exists() && !f.delete()) {
                // 文件删除失败不阻断（记录删除即可）
            }
        } catch (Exception ignored) {
        }
        questionImageDao.deleteById(id);
        return R.ok("已删除");
    }

    @PostMapping("/questions/ai-generate")
    public R aiGenerate(@RequestBody Map<String, Object> body) {
        try {
            String material = null;
            // 支持 materialId：从已上传资源读取文本（优先于 material 文本字段）
            if (body.get("materialId") != null) {
                Long materialId = ((Number) body.get("materialId")).longValue();
                AiMaterial m = aiMaterialDao.selectById(materialId);
                if (m == null) return R.badRequest("资源不存在或已被删除");
                try (InputStream is = new java.io.FileInputStream(new java.io.File(m.getFilePath()).getAbsoluteFile())) {
                    material = TextExtractor.extract(m.getFileName(), is.readAllBytes());
                } catch (Exception e) {
                    return R.badRequest("资源文本提取失败: " + e.getMessage());
                }
            } else {
                material = (String) body.get("material");
            }
            String type = (String) body.getOrDefault("type", "ESSAY");
            int count = body.get("count") != null ? ((Number) body.get("count")).intValue() : 5;
            String difficulty = (String) body.getOrDefault("difficulty", "MEDIUM");
            return R.ok().data(questionAiService.generateDrafts(material, type, count, difficulty));
        } catch (RuntimeException e) {
            return R.badRequest(e.getMessage());
        }
    }

    @PostMapping("/questions/ai-generate/file")
    public R aiGenerateFromFile(@RequestParam("file") MultipartFile file,
                                @RequestParam(defaultValue = "ESSAY") String type,
                                @RequestParam(defaultValue = "5") int count,
                                @RequestParam(defaultValue = "MEDIUM") String difficulty) {
        try {
            String material = TextExtractor.extract(file);
            return R.ok().data(questionAiService.generateDrafts(material, type, count, difficulty));
        } catch (Exception e) {
            return R.badRequest("材料提取失败: " + e.getMessage());
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
        if (tc.getName() == null || tc.getName().isBlank()) {
            return R.badRequest("教学班名称不能为空");
        }
        if (tc.getTeacherId() == null) {
            return R.badRequest("请指定负责教师");
        }
        // 课程类型：默认必修（专业课）；选修教学班由教师自由选学生
        if (tc.getCourseType() == null || tc.getCourseType().isBlank()) {
            tc.setCourseType("REQUIRED");
        } else if (!"REQUIRED".equals(tc.getCourseType()) && !"ELECTIVE".equals(tc.getCourseType())) {
            return R.badRequest("课程类型不合法（REQUIRED/ELECTIVE）");
        }
        teachingClassDao.insert(tc);
        return R.ok().data(tc);
    }

    @GetMapping("/teaching-classes/{id}")
    public R getTeachingClass(@PathVariable Long id) {
        TeachingClass tc = teachingClassDao.selectById(id);
        if (tc == null) return R.badRequest("教学班不存在");
        List<Clazz> classes = clazzDao.selectList(new LambdaQueryWrapper<Clazz>()
                .inSql(Clazz::getId, "SELECT clazz_id FROM teaching_class_clazz WHERE teaching_class_id = " + id));
        return R.ok().data(Map.of("teachingClass", tc, "classes", classes));
    }

    @PutMapping("/teaching-classes/{id}")
    public R updateTeachingClass(@PathVariable Long id, @RequestBody TeachingClass tc) {
        TeachingClass exist = teachingClassDao.selectById(id);
        if (exist == null) return R.badRequest("教学班不存在");
        if (tc.getName() != null && !tc.getName().isBlank()) exist.setName(tc.getName());
        if (tc.getTeacherId() != null) exist.setTeacherId(tc.getTeacherId());
        teachingClassDao.updateById(exist);
        return R.ok().data(exist);
    }

    @DeleteMapping("/teaching-classes/{id}")
    public R deleteTeachingClass(@PathVariable Long id) {
        teachingClassDao.deleteById(id);
        return R.ok();
    }
}
