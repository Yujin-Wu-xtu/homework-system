package com.xtu.homework.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xtu.homework.dao.*;
import com.xtu.homework.entity.*;
import com.xtu.homework.service.QuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 题目服务实现类
 * 负责题目的增删改查、批量导入、查重检测、知识点关联等业务
 */
@Service
@RequiredArgsConstructor
public class QuestionServiceImpl extends ServiceImpl<QuestionDao, Question>
        implements QuestionService {

    private final QuestionDao questionDao;
    private final QuestionOptionDao questionOptionDao;
    private final QuestionKnowledgeDao questionKnowledgeDao;
    private static final double DUPLICATE_THRESHOLD = 0.80;

    @Override
    @Transactional
    public Question addQuestion(Question question, List<QuestionOption> options,
                                List<Long> knowledgePointIds) {
        // 客观题必须有标准答案
        if (!"ESSAY".equals(question.getType()) &&
                (question.getCorrectAnswer() == null || question.getCorrectAnswer().isBlank())) {
            throw new RuntimeException("客观题必须录入标准答案");
        }
        question.setStatus("ACTIVE");
        questionDao.insert(question);

        if (options != null) {
            for (QuestionOption opt : options) {
                opt.setQuestionId(question.getId());
                questionOptionDao.insert(opt);
            }
        }
        if (knowledgePointIds != null) {
            for (Long kpId : knowledgePointIds) {
                QuestionKnowledge qk = new QuestionKnowledge();
                qk.setQuestionId(question.getId());
                qk.setKnowledgePointId(kpId);
                questionKnowledgeDao.insert(qk);
            }
        }
        return question;
    }

    @Override
    @Transactional
    public int batchImport(List<Question> questions) {
        int success = 0;
        for (Question q : questions) {
            try {
                addQuestion(q, null, null);
                success++;
            } catch (Exception ignored) {
                // 单条失败不影响其他题目
            }
        }
        return success;
    }

    @Override
    public List<Question> checkDuplicate(String content, String type) {
        // 基于 Jaccard 相似度的简化查重算法
        LambdaQueryWrapper<Question> qw = new LambdaQueryWrapper<>();
        qw.eq(Question::getType, type).eq(Question::getStatus, "ACTIVE");
        // 用前50字符做初筛
        String prefix = content.length() > 50 ? content.substring(0, 50) : content;
        qw.likeRight(Question::getContent, prefix);
        List<Question> candidates = questionDao.selectList(qw);

        List<Question> duplicates = new ArrayList<>();
        Set<String> set1 = toCharSet(content);
        for (Question c : candidates) {
            Set<String> set2 = toCharSet(c.getContent());
            Set<String> inter = new HashSet<>(set1);
            inter.retainAll(set2);
            Set<String> union = new HashSet<>(set1);
            union.addAll(set2);
            double sim = union.isEmpty() ? 0 : (double) inter.size() / union.size();
            if (sim >= DUPLICATE_THRESHOLD) {
                duplicates.add(c);
            }
        }
        return duplicates;
    }

    @Override
    public void toggleStatus(Long questionId) {
        Question q = questionDao.selectById(questionId);
        q.setStatus("ACTIVE".equals(q.getStatus()) ? "DISABLED" : "ACTIVE");
        questionDao.updateById(q);
    }

    @Override
    public Page<Question> searchQuestions(int page, int size, String keyword,
                                          String type, String difficulty,
                                          Long knowledgePointId) {
        LambdaQueryWrapper<Question> qw = new LambdaQueryWrapper<>();
        qw.eq(Question::getStatus, "ACTIVE");
        if (type != null && !type.isBlank()) qw.eq(Question::getType, type);
        if (difficulty != null && !difficulty.isBlank()) qw.eq(Question::getDifficulty, difficulty);
        if (keyword != null && !keyword.isBlank()) qw.like(Question::getContent, keyword);
        if (knowledgePointId != null) {
            qw.inSql(Question::getId,
                    "SELECT question_id FROM question_knowledge WHERE knowledge_point_id = "
                            + knowledgePointId);
        }
        return questionDao.selectPage(new Page<>(page, size), qw);
    }

    @Override
    public List<QuestionOption> getOptions(Long questionId) {
        return questionOptionDao.selectList(
                new LambdaQueryWrapper<QuestionOption>()
                        .eq(QuestionOption::getQuestionId, questionId)
                        .orderByAsc(QuestionOption::getSortOrder));
    }

    private Set<String> toCharSet(String text) {
        String cleaned = text.replaceAll("[\\pP\\s]", "").toLowerCase();
        Set<String> set = new HashSet<>();
        for (char c : cleaned.toCharArray()) {
            set.add(String.valueOf(c));
        }
        return set;
    }
}
