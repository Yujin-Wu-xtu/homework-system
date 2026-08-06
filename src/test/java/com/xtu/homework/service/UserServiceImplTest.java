package com.xtu.homework.service;

import com.xtu.homework.HomeworkApplication;
import com.xtu.homework.entity.User;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用户服务单元测试
 */
@SpringBootTest(classes = HomeworkApplication.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserServiceImplTest {

    @Autowired
    private UserService userService;

    private static Long testTeacherId;

    // ========== 登录测试 ==========

    @Test
    @Order(1)
    void testLoginSuccess() {
        String token = userService.login("admin", "Admin123456");
        assertNotNull(token);
        assertTrue(token.length() > 20);
    }

    @Test
    @Order(2)
    void testLoginWrongPassword() {
        assertThrows(RuntimeException.class, () ->
                userService.login("admin", "WrongPassword"));
    }

    @Test
    @Order(3)
    void testLoginNonExistentUser() {
        assertThrows(RuntimeException.class, () ->
                userService.login("nobody_user_xyz", "Whatever123"));
    }

    // ========== 密码测试 ==========

    @Test
    @Order(4)
    void testChangePasswordWeak() {
        assertThrows(RuntimeException.class, () ->
                userService.changePassword(1L, "Admin123456", "123"));
    }

    @Test
    @Order(5)
    void testResetPassword() {
        String newPwd = userService.resetPassword(1L);
        assertNotNull(newPwd);
        assertTrue(newPwd.length() >= 10);
    }

    // ========== 教师CRUD ==========

    @Test
    @Order(6)
    void testAddTeacher() {
        User teacher = new User();
        teacher.setUsername("T_test_" + System.currentTimeMillis());
        teacher.setRealName("测试教师");
        User saved = userService.addTeacher(teacher);
        assertNotNull(saved.getId());
        assertEquals("TEACHER", saved.getRole());
        assertEquals("ACTIVE", saved.getStatus());
        testTeacherId = saved.getId();
    }

    @Test
    @Order(7)
    void testListTeachers() {
        var page = userService.listTeachers(1, 10, null);
        assertNotNull(page);
        assertNotNull(page.getRecords());
        // 验证查询不抛异常即可
    }

    @Test
    @Order(8)
    void testListTeachersWithKeyword() {
        var page = userService.listTeachers(1, 10, "测试");
        assertNotNull(page);
        assertNotNull(page.getRecords());
    }

    // ========== 学生导入 ==========

    @Test
    @Order(9)
    void testImportStudents() {
        List<User> students = new ArrayList<>();
        User s1 = new User();
        s1.setUsername("testimport" + System.currentTimeMillis());
        s1.setRealName("导入学生1");
        students.add(s1);
        int count = userService.importStudents(1L, students);
        assertEquals(1, count);
    }

    // ========== 学生转班 ==========

    @Test
    @Order(10)
    void testTransferStudent() {
        // 学生3(张三, clazz_id=1) 转去班级2
        assertDoesNotThrow(() ->
                userService.transferStudent(3L, 1L, 2L));
        // 转回班级1
        assertDoesNotThrow(() ->
                userService.transferStudent(3L, 2L, 1L));
    }

    // ========== 学生新增 / 删除 ==========

    @Test
    @Order(11)
    void testAddStudent() {
        User s = new User();
        s.setUsername("S_test_" + System.currentTimeMillis());
        s.setRealName("测试学生");
        User saved = userService.addStudent(1L, s);
        assertNotNull(saved.getId());
        assertEquals("STUDENT", saved.getRole());
        assertEquals("ACTIVE", saved.getStatus());
        assertEquals(1L, saved.getClazzId());
        // 初始密码应为随机强密码（非学号后6位），且要求首次登录重置
        assertTrue(saved.getPwdResetRequired());
        assertFalse(saved.getPassword().startsWith(saved.getUsername().substring(0, 6)));
    }

    @Test
    @Order(12)
    void testAddStudentDuplicateUsername() {
        User s = new User();
        s.setUsername("20240001"); // 与初始数据张三重复
        s.setRealName("重复学号学生");
        assertThrows(RuntimeException.class, () -> userService.addStudent(1L, s));
    }

    @Test
    @Order(13)
    void testAddStudentNonexistentClass() {
        User s = new User();
        s.setUsername("S_noclass_" + System.currentTimeMillis());
        s.setRealName("无班级学生");
        assertThrows(RuntimeException.class, () -> userService.addStudent(99999L, s));
    }

    @Test
    @Order(14)
    void testDeleteStudent() {
        User s = new User();
        s.setUsername("S_del_" + System.currentTimeMillis());
        s.setRealName("待删除学生");
        User saved = userService.addStudent(1L, s);
        userService.deleteStudent(saved.getId());
        User after = userService.getById(saved.getId());
        assertEquals("DISABLED", after.getStatus(), "删除应为软删（禁用账号）");
    }

    @Test
    @Order(15)
    void testDeleteNonexistentStudent() {
        assertThrows(RuntimeException.class, () -> userService.deleteStudent(99999L));
    }
}
