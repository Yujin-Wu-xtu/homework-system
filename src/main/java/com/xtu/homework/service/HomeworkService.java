package com.xtu.homework.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xtu.homework.dto.HomeworkAssignDto;
import com.xtu.homework.entity.Homework;
import java.util.List;
import java.util.Map;

public interface HomeworkService extends IService<Homework> {
    Homework assignHomework(Long teacherId, HomeworkAssignDto dto);
    Homework updateHomework(Long homeworkId, HomeworkAssignDto dto);
    void deleteHomework(Long homeworkId);
    Homework closeHomework(Long homeworkId);
    List<Map<String, Object>> getSubmissionStatus(Long homeworkId);
    byte[] exportGrades(Long homeworkId);
    Page<Homework> listStudentHomeworks(Long studentId, int page, int size);
    Map<String, Object> getHomeworkDetail(Long homeworkId, Long studentId);
}
