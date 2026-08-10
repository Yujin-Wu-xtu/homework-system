package com.xtu.homework.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xtu.homework.dao.*;
import com.xtu.homework.dto.HomeworkAssignDto;
import com.xtu.homework.entity.*;
import com.xtu.homework.service.HomeworkService;
import com.xtu.homework.service.StudentHomeworkAccessService;
import com.xtu.homework.service.UserService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 作业服务实现类
 * 负责作业的布置、修改、删除、提交情况查看、成绩导出等核心业务
 */
@Service
@RequiredArgsConstructor
public class HomeworkServiceImpl extends ServiceImpl<HomeworkDao, Homework>
        implements HomeworkService {

    private final HomeworkDao homeworkDao;
    private final HomeworkQuestionDao hwQuestionDao;
    private final SubmissionDao submissionDao;
    private final SubmissionAnswerDao submissionAnswerDao;
    private final UserDao userDao;
    private final QuestionDao questionDao;
    private final QuestionOptionDao questionOptionDao;
    private final StudentHomeworkAccessService studentHomeworkAccessService;
    private final UserService userService;

    @Override
    @Transactional
    public Homework assignHomework(Long teacherId, HomeworkAssignDto dto) {
        // 1. 创建作业记录
        Homework hw = new Homework();
        hw.setTitle(dto.getTitle());
        hw.setDescription(dto.getDescription());
        hw.setTeachingClassId(dto.getTeachingClassId());
        hw.setTeacherId(teacherId);
        hw.setDeadline(dto.getDeadline());
        hw.setTotalScore(dto.getQuestions().stream()
                .map(HomeworkAssignDto.QuestionItem::getScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        hw.setStatus("PUBLISHED");
        hw.setQuestionLocked(false);
        homeworkDao.insert(hw);

        // 2. 关联题目
        for (HomeworkAssignDto.QuestionItem qi : dto.getQuestions()) {
            HomeworkQuestion hq = new HomeworkQuestion();
            hq.setHomeworkId(hw.getId());
            hq.setQuestionId(qi.getQuestionId());
            hq.setSortOrder(qi.getSortOrder());
            hq.setScore(qi.getScore());
            hwQuestionDao.insert(hq);
        }

        // 3. 为教学班的所有学生初始化提交记录（按课程类型分流：必修=自然班动态 / 选修=静态关系）
        List<User> students = userService.findTeachingClassStudents(dto.getTeachingClassId());
        if (students.isEmpty()) {
            // 回滚已创建的数据：先删题目关联（外键），再删作业
            hwQuestionDao.delete(new LambdaQueryWrapper<HomeworkQuestion>()
                    .eq(HomeworkQuestion::getHomeworkId, hw.getId()));
            homeworkDao.deleteById(hw.getId());
            throw new RuntimeException("该教学班尚未包含任何学生（请先在'教学班管理'中加入学生），发布后学生将看不到此作业");
        }
        for (User student : students) {
            Submission sub = new Submission();
            sub.setHomeworkId(hw.getId());
            sub.setStudentId(student.getId());
            sub.setStatus("NOT_SUBMITTED");
            submissionDao.insert(sub);
        }
        return hw;
    }

    @Override
    public Homework updateHomework(Long homeworkId, HomeworkAssignDto dto) {
        Homework hw = homeworkDao.selectById(homeworkId);
        long submittedCount = submissionDao.selectCount(
                new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getHomeworkId, homeworkId)
                        .ne(Submission::getStatus, "NOT_SUBMITTED"));
        if (submittedCount > 0) {
            throw new RuntimeException("已有学生提交，不可修改题目内容");
        }
        hw.setTitle(dto.getTitle());
        hw.setDescription(dto.getDescription());
        hw.setDeadline(dto.getDeadline());
        hw.setTotalScore(dto.getQuestions().stream()
                .map(HomeworkAssignDto.QuestionItem::getScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        homeworkDao.updateById(hw);
        return hw;
    }

    @Override
    public void deleteHomework(Long homeworkId) {
        long count = submissionDao.selectCount(
                new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getHomeworkId, homeworkId)
                        .ne(Submission::getStatus, "NOT_SUBMITTED"));
        if (count > 0) {
            throw new RuntimeException("已有学生提交，不可删除作业");
        }
        // 清理关联（外键约束）：作业-题目关联 + 预生成的未提交记录
        hwQuestionDao.delete(new LambdaQueryWrapper<HomeworkQuestion>()
                .eq(HomeworkQuestion::getHomeworkId, homeworkId));
        submissionDao.delete(new LambdaQueryWrapper<Submission>()
                .eq(Submission::getHomeworkId, homeworkId));
        homeworkDao.deleteById(homeworkId);
    }

    @Override
    @Transactional
    public Homework closeHomework(Long homeworkId) {
        Homework hw = homeworkDao.selectById(homeworkId);
        if (hw == null) throw new RuntimeException("作业不存在");
        if ("CLOSED".equals(hw.getStatus())) return hw;   // 幂等
        if (!"PUBLISHED".equals(hw.getStatus())) throw new RuntimeException("仅发布中的作业可关闭");
        hw.setStatus("CLOSED");
        homeworkDao.updateById(hw);
        return hw;
    }

    @Override
    public List<Map<String, Object>> getSubmissionStatus(Long homeworkId) {
        List<Submission> subs = submissionDao.selectList(
                new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getHomeworkId, homeworkId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Submission sub : subs) {
            User student = userDao.selectById(sub.getStudentId());
            Map<String, Object> m = new HashMap<>();
            m.put("studentId", student.getId());
            m.put("studentName", student.getRealName());
            m.put("username", student.getUsername());
            m.put("status", sub.getStatus());
            m.put("submitTime", sub.getSubmitTime());
            m.put("autoScore", sub.getAutoScore());
            m.put("manualScore", sub.getManualScore());
            m.put("totalScore", sub.getTotalScore());
            result.add(m);
        }
        return result;
    }

    @Override
    public byte[] exportGrades(Long homeworkId) {
        List<Map<String, Object>> data = getSubmissionStatus(homeworkId);
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("成绩单");
        Row header = sheet.createRow(0);
        String[] headers = {"学号", "姓名", "状态", "提交时间", "客观题得分", "主观题得分", "总分"};
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }
        int rowIdx = 1;
        for (Map<String, Object> d : data) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue((String) d.get("username"));
            row.createCell(1).setCellValue((String) d.get("studentName"));
            row.createCell(2).setCellValue((String) d.get("status"));
            row.createCell(3).setCellValue(String.valueOf(d.get("submitTime")));
            row.createCell(4).setCellValue(String.valueOf(d.getOrDefault("autoScore", "")));
            row.createCell(5).setCellValue(String.valueOf(d.getOrDefault("manualScore", "")));
            row.createCell(6).setCellValue(String.valueOf(d.getOrDefault("totalScore", "")));
        }
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            wb.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("导出Excel失败: " + e.getMessage());
        }
    }

    @Override
    public Page<Homework> listStudentHomeworks(Long studentId, int page, int size) {
        List<Long> currentTeachingClassIds = studentHomeworkAccessService.currentTeachingClassIds(studentId);
        if (currentTeachingClassIds.isEmpty()) return new Page<>();
        LambdaQueryWrapper<Submission> subQw = new LambdaQueryWrapper<>();
        subQw.eq(Submission::getStudentId, studentId);
        List<Submission> subs = submissionDao.selectList(subQw);
        if (subs.isEmpty()) return new Page<>();
        List<Long> hwIds = subs.stream().map(Submission::getHomeworkId).distinct().toList();
        Page<Homework> result = homeworkDao.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Homework>()
                        .in(Homework::getId, hwIds)
                        .in(Homework::getTeachingClassId, currentTeachingClassIds)
                        .orderByDesc(Homework::getCreateTime));
        // 学生视角状态：status 覆盖为该学生的提交状态（NOT_SUBMITTED/SUBMITTED/GRADED），
        // 而非作业全局状态（PUBLISHED/CLOSED）——前端"待完成/已完成"过滤与操作按钮均按提交状态判断，
        // 否则发布中的作业（PUBLISHED）会被前端两个 tab 同时滤掉，学生永远看不到作业
        Map<Long, String> subStatus = new HashMap<>();
        for (Submission s : subs) subStatus.put(s.getHomeworkId(), s.getStatus());
        for (Homework h : result.getRecords()) {
            // 已关闭的作业保持全局状态 CLOSED（学生端只读，进"已完成"tab，不再提供提交/修改入口）
            if ("CLOSED".equals(h.getStatus())) continue;
            String st = subStatus.get(h.getId());
            if (st != null) h.setStatus(st);
        }
        return result;
    }

    @Override
    public Map<String, Object> getHomeworkDetail(Long homeworkId, Long studentId) {
        Homework hw = studentHomeworkAccessService.requireAccess(homeworkId, studentId);

        List<HomeworkQuestion> hqList = hwQuestionDao.selectList(
                new LambdaQueryWrapper<HomeworkQuestion>()
                        .eq(HomeworkQuestion::getHomeworkId, homeworkId)
                        .orderByAsc(HomeworkQuestion::getSortOrder));

        // 题目随机排序（不同学生使用不同的随机种子）
        long seed = studentId + homeworkId;
        Collections.shuffle(hqList, new Random(seed));

        List<Map<String, Object>> questions = new ArrayList<>();
        for (HomeworkQuestion hq : hqList) {
            Question q = questionDao.selectById(hq.getQuestionId());
            Map<String, Object> qm = new HashMap<>();
            qm.put("id", q.getId());
            qm.put("type", q.getType());
            qm.put("content", q.getContent());
            qm.put("score", hq.getScore());
            // 安全：只返回题干/选项/分值，绝不返回 correctAnswer/referenceAnswer（学生端可见标准答案 = 作弊漏洞）
            if (q.isObjective()) {
                List<QuestionOption> options = questionOptionDao.selectList(
                        new LambdaQueryWrapper<QuestionOption>()
                                .eq(QuestionOption::getQuestionId, q.getId())
                                .orderByAsc(QuestionOption::getLabel));
                qm.put("options", options);
            }
            questions.add(qm);
        }

        Submission sub = submissionDao.selectOne(
                new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getHomeworkId, homeworkId)
                        .eq(Submission::getStudentId, studentId));

        // 学生已提交答案（修改答案回显：前端富文本编辑器按此初始化）
        Map<Long, String> answerMap = new HashMap<>();
        if (sub != null) {
            submissionAnswerDao.selectList(
                            new LambdaQueryWrapper<SubmissionAnswer>()
                                    .eq(SubmissionAnswer::getSubmissionId, sub.getId()))
                    .forEach(a -> answerMap.put(a.getQuestionId(), a.getStudentAnswer()));
        }
        for (Map<String, Object> qm : questions) {
            qm.put("answer", answerMap.getOrDefault(((Number) qm.get("id")).longValue(), ""));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("homework", hw);
        result.put("questions", questions);
        result.put("submission", sub);
        result.put("canModify", sub != null && sub.getSubmitTime() != null &&
                LocalDateTime.now().isBefore(hw.getDeadline()));
        return result;
    }
}
