package com.xtu.homework.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xtu.homework.entity.Clazz;
import com.xtu.homework.entity.User;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UserService extends IService<User> {
    String login(String username, String password);
    void changePassword(Long userId, String oldPwd, String newPwd);
    String resetPassword(Long userId);
    Page<User> listTeachers(int page, int size, String keyword);
    User addTeacher(User teacher);
    int importStudents(Long clazzId, List<User> students);
    void transferStudent(Long studentId, Long fromClazzId, Long toClazzId);
    List<Clazz> importClasses(MultipartFile file);
    int importStudentsFromExcel(Long clazzId, MultipartFile file);
}
