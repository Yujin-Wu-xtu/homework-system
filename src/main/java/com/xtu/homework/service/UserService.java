package com.xtu.homework.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xtu.homework.entity.Clazz;
import com.xtu.homework.entity.User;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface UserService extends IService<User> {
    String login(String username, String password);
    void changePassword(Long userId, String oldPwd, String newPwd);
    void updateProfile(Long userId, String realName, String phone, String email);

    /** 邮箱注册：校验验证码 + 用户名/邮箱唯一性，注册为 STUDENT 角色 */
    void registerByEmail(String username, String email, String password, String code);
    String resetPassword(Long userId);
    Page<User> listTeachers(int page, int size, String keyword);
    User addTeacher(User teacher);
    User addStudent(Long clazzId, User student);

    /**
     * 学生进入自然班后自动补齐该班关联教学班已发布/已关闭作业的 submission（幂等），
     * 由 addStudent / importStudents / importStudentsFromExcel / transferStudent 内部调用
     */
    void syncStudentToTeachingClasses(Long studentId, Long clazzId);
    void deleteStudent(Long studentId);
    int importStudents(Long clazzId, List<User> students);
    void transferStudent(Long studentId, Long fromClazzId, Long toClazzId);
    List<Clazz> importClasses(MultipartFile file);
    Map<String, Object> importStudentsFromExcel(Long clazzId, MultipartFile file);
}
