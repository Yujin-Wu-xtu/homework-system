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

    // ========== 教学班学生（按课程类型分流：必修=自然班动态查询，选修=teaching_class_student 静态关系）==========

    /** 教学班内学生列表（分流）——作业预生成/学生列表/重置密码统一入口 */
    List<User> findTeachingClassStudents(Long tcId);

    /** 教学班内学生人数（分流，前端过滤空教学班/布置作业下拉显示人数） */
    long countTeachingClassStudents(Long tcId);

    /** 选修教学班拉入学生（静态关系，去重；同时补该教学班已发布作业的 submission 使作业立即可见） */
    int addElectiveStudents(Long tcId, List<Long> studentIds);

    /** 选修教学班移除学生（历史 submission 保留供教师审计，学生端访问由实时鉴权立即失效） */
    void removeElectiveStudent(Long tcId, Long studentId);
}
