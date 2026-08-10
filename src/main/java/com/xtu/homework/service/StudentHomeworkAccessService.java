package com.xtu.homework.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xtu.homework.dao.HomeworkDao;
import com.xtu.homework.dao.TeachingClassClazzDao;
import com.xtu.homework.dao.TeachingClassStudentDao;
import com.xtu.homework.dao.UserDao;
import com.xtu.homework.entity.Homework;
import com.xtu.homework.entity.TeachingClassClazz;
import com.xtu.homework.entity.TeachingClassStudent;
import com.xtu.homework.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 学生作业访问资格：依据学生当前归属实时判断，不依据历史 submission 记录。
 * 学生可见教学班 = 必修（自然班级动态查询）∪ 选修（teaching_class_student 静态关系）。
 */
@Service
@RequiredArgsConstructor
public class StudentHomeworkAccessService {

    private final UserDao userDao;
    private final HomeworkDao homeworkDao;
    private final TeachingClassClazzDao teachingClassClazzDao;
    private final TeachingClassStudentDao teachingClassStudentDao;

    public List<Long> currentTeachingClassIds(Long studentId) {
        User student = userDao.selectById(studentId);
        if (student == null || !"STUDENT".equals(student.getRole())) {
            return Collections.emptyList();
        }
        List<Long> ids = new ArrayList<>();
        // 必修教学班：学生自然班级 ∈ teaching_class_clazz
        if (student.getClazzId() != null) {
            teachingClassClazzDao.selectList(
                            new LambdaQueryWrapper<TeachingClassClazz>()
                                    .eq(TeachingClassClazz::getClazzId, student.getClazzId()))
                    .forEach(tcc -> ids.add(tcc.getTeachingClassId()));
        }
        // 选修教学班：teaching_class_student 静态关系（与自然班级无关，转班不影响选修归属）
        teachingClassStudentDao.selectList(
                        new LambdaQueryWrapper<TeachingClassStudent>()
                                .eq(TeachingClassStudent::getStudentId, studentId))
                .forEach(tcs -> ids.add(tcs.getTeachingClassId()));
        return ids.stream().distinct().toList();
    }

    public boolean canAccess(Long homeworkId, Long studentId) {
        Homework homework = homeworkDao.selectById(homeworkId);
        if (homework == null) return false;
        return currentTeachingClassIds(studentId).contains(homework.getTeachingClassId());
    }

    public Homework requireAccess(Long homeworkId, Long studentId) {
        Homework homework = homeworkDao.selectById(homeworkId);
        if (homework == null) throw new RuntimeException("作业不存在");
        if (!canAccess(homeworkId, studentId)) {
            throw new RuntimeException("该作业不属于当前教学班，无法访问");
        }
        return homework;
    }
}
