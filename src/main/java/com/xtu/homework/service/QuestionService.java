package com.xtu.homework.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xtu.homework.entity.Question;
import com.xtu.homework.entity.QuestionOption;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface QuestionService extends IService<Question> {
    Question addQuestion(Question q, List<QuestionOption> options, List<Long> kpIds);
    int batchImport(List<Question> questions);
    Map<String, Object> importQuestionsFromExcel(MultipartFile file);
    List<Question> checkDuplicate(String content, String type);
    void toggleStatus(Long questionId);
    Page<Question> searchQuestions(int page, int size, String keyword,
                                   String type, String difficulty, Long kpId);
    List<QuestionOption> getOptions(Long questionId);
}
