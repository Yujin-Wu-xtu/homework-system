package com.xtu.homework.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xtu.homework.dao.*;
import com.xtu.homework.dto.SubmissionDto;
import com.xtu.homework.entity.*;
import com.xtu.homework.service.SubmissionService;
import com.xtu.homework.service.StudentHomeworkAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 提交与评分服务实现类
 * 负责学生作业提交、客观题自动评分、教师主观题评分等核心业务
 */
@Service
@RequiredArgsConstructor
public class SubmissionServiceImpl extends ServiceImpl<SubmissionDao, Submission>
        implements SubmissionService {

    private final SubmissionDao submissionDao;
    private final SubmissionAnswerDao answerDao;
    private final HomeworkDao homeworkDao;
    private final HomeworkQuestionDao hwQuestionDao;
    private final QuestionDao questionDao;
    private final UserDao userDao;
    private final StudentHomeworkAccessService studentHomeworkAccessService;

    @Override
    @Transactional
    public Submission submit(Long studentId, SubmissionDto dto) {
        Homework hw = studentHomeworkAccessService.requireAccess(dto.getHomeworkId(), studentId);
        if (LocalDateTime.now().isAfter(hw.getDeadline())) {
            throw new RuntimeException("已超过提交截止时间");
        }

        Submission sub = submissionDao.selectOne(
                new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getHomeworkId, dto.getHomeworkId())
                        .eq(Submission::getStudentId, studentId));
        if (sub == null) {
            sub = new Submission();
            sub.setHomeworkId(dto.getHomeworkId());
            sub.setStudentId(studentId);
            sub.setOpenTime(LocalDateTime.now());
            submissionDao.insert(sub);
        }

        // 删除旧答案（修改提交场景）
        answerDao.delete(new LambdaQueryWrapper<SubmissionAnswer>()
                .eq(SubmissionAnswer::getSubmissionId, sub.getId()));

        // 保存答案并自动评分客观题
        BigDecimal autoScore = BigDecimal.ZERO;
        for (SubmissionDto.AnswerItem item : dto.getAnswers()) {
            Question q = questionDao.selectById(item.getQuestionId());
            SubmissionAnswer answer = new SubmissionAnswer();
            answer.setSubmissionId(sub.getId());
            answer.setQuestionId(item.getQuestionId());
            answer.setStudentAnswer(item.getAnswer());

            if (!"ESSAY".equals(q.getType()) && q.getCorrectAnswer() != null) {
                // 获取该题在本次作业中的分值
                HomeworkQuestion hq = hwQuestionDao.selectOne(
                        new LambdaQueryWrapper<HomeworkQuestion>()
                                .eq(HomeworkQuestion::getHomeworkId, dto.getHomeworkId())
                                .eq(HomeworkQuestion::getQuestionId, item.getQuestionId()));
                boolean correct = q.getCorrectAnswer().trim()
                        .equalsIgnoreCase(item.getAnswer() != null ? item.getAnswer().trim() : "");
                BigDecimal score = correct && hq != null ? hq.getScore() : BigDecimal.ZERO;
                answer.setScore(score);
                autoScore = autoScore.add(score);
            }
            answerDao.insert(answer);
        }

        // 更新提交记录
        LocalDateTime now = LocalDateTime.now();
        sub.setSubmitTime(now);
        sub.setLastModifiedTime(now);
        sub.setStatus("SUBMITTED");
        sub.setAutoScore(autoScore);
        if (sub.getOpenTime() != null) {
            sub.setDurationSeconds((int) Duration.between(sub.getOpenTime(), now).getSeconds());
        }
        // 异常检测：答题时间异常短
        if (sub.getDurationSeconds() != null
                && sub.getDurationSeconds() < dto.getAnswers().size() * 10) {
            sub.setSuspiciousFlag(true);
        }
        submissionDao.updateById(sub);
        return sub;
    }

    @Override
    @Transactional
    public Submission modifyAnswer(Long studentId, SubmissionDto dto) {
        Homework hw = homeworkDao.selectById(dto.getHomeworkId());
        if (LocalDateTime.now().isAfter(hw.getDeadline())) {
            throw new RuntimeException("已超过提交截止时间，不可修改答案");
        }
        return submit(studentId, dto); // 复用提交逻辑
    }

    @Override
    @Transactional
    public void gradeAnswer(Long answerId, Long teacherId,
                            BigDecimal score, String comment) {
        SubmissionAnswer answer = answerDao.selectById(answerId);
        if (answer == null) throw new RuntimeException("答案记录不存在");
        answer.setScore(score);
        answer.setComment(comment);
        answer.setGradedBy(teacherId);
        answer.setGradedTime(LocalDateTime.now());
        answerDao.updateById(answer);

        // 重新计算提交记录的总分
        Submission sub = submissionDao.selectById(answer.getSubmissionId());
        List<SubmissionAnswer> allAnswers = answerDao.selectList(
                new LambdaQueryWrapper<SubmissionAnswer>()
                        .eq(SubmissionAnswer::getSubmissionId, sub.getId()));
        BigDecimal total = allAnswers.stream()
                .map(a -> a.getScore() == null ? BigDecimal.ZERO : a.getScore())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        sub.setTotalScore(total);
        sub.setStatus("GRADED");
        submissionDao.updateById(sub);
    }

    @Override
    public List<Submission> getUngradedList(Long homeworkId) {
        return submissionDao.selectList(
                new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getHomeworkId, homeworkId)
                        .in(Submission::getStatus, "SUBMITTED", "GRADED"));
    }

    @Override
    public Map<String, Object> getGradingPage(Long homeworkId, int page, int size, String status) {
        LambdaQueryWrapper<Submission> qw = new LambdaQueryWrapper<Submission>()
                .eq(Submission::getHomeworkId, homeworkId);
        if (status != null && !status.isBlank() && !"ALL".equals(status)) {
            // UNGRADED → 仅待批改（SUBMITTED）；GRADED → 已批改
            qw.eq(Submission::getStatus, "UNGRADED".equals(status) ? "SUBMITTED" : status);
        }
        qw.orderByAsc(Submission::getStudentId);
        Page<Submission> p = submissionDao.selectPage(new Page<>(page, size), qw);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Submission s : p.getRecords()) {
            User stu = userDao.selectById(s.getStudentId());
            rows.add(Map.of(
                    "submissionId", s.getId(),
                    "studentId", s.getStudentId(),
                    "studentName", stu != null ? stu.getRealName() : "未知",
                    "status", s.getStatus(),
                    "totalScore", s.getTotalScore() == null ? "" : s.getTotalScore(),
                    "submitTime", s.getSubmitTime() == null ? "" : s.getSubmitTime().toString()));
        }
        Map<String, Object> result = new HashMap<>();
        result.put("records", rows);
        result.put("total", p.getTotal());
        result.put("page", page);
        result.put("size", size);
        return result;
    }
}
