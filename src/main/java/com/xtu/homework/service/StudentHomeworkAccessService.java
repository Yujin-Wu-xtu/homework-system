package com.xtu.homework.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xtu.homework.dao.HomeworkDao;
import com.xtu.homework.dao.TeachingClassClazzDao;
import com.xtu.homework.dao.UserDao;
import com.xtu.homework.entity.Homework;
import com.xtu.homework.entity.TeachingClassClazz;
import com.xtu.homework.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 学生作业访问资格：只依据学生当前自然班动态判断，不依据历史 submission 记录。
 */
@Service
@RequiredArgsConstructor
public class StudentHomeworkAccessService {

    private final UserDao userDao;
    private final HomeworkDao homeworkDao;
    private final TeachingClassClazzDao teachingClassClazzDao;

    public List<Long> currentTeachingClassIds(Long studentId) {
        User student = userDao.selectById(studentId);
        if (student == null || !"STUDENT".equals(student.getRole()) || student.getClazzId() == null) {
            return Collections.emptyList();
        }
        return teachingClassClazzDao.selectList(
                        new LambdaQueryWrapper<TeachingClassClazz>()
                                .eq(TeachingClassClazz::getClazzId, student.getClazzId()))
                .stream()
                .map(TeachingClassClazz::getTeachingClassId)
                .distinct()
                .toList();
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
