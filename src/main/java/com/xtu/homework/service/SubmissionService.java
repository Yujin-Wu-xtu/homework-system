package com.xtu.homework.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xtu.homework.dto.SubmissionDto;
import com.xtu.homework.entity.Submission;
import java.math.BigDecimal;
import java.util.List;

public interface SubmissionService extends IService<Submission> {
    Submission submit(Long studentId, SubmissionDto dto);
    Submission modifyAnswer(Long studentId, SubmissionDto dto);
    void gradeAnswer(Long answerId, Long teacherId, BigDecimal score, String comment);
    List<Submission> getUngradedList(Long homeworkId);
}
