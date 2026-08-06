package com.xtu.homework.service;

import com.xtu.homework.HomeworkApplication;
import com.xtu.homework.entity.User;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    // ========== Excel 导入（列名匹配 + 排错）==========

    private MultipartFile buildXlsx(String[] header, String[][] rows) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("学生");
            Row h = sheet.createRow(0);
            for (int i = 0; i < header.length; i++) h.createCell(i).setCellValue(header[i]);
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    if (rows[r][c] != null && !rows[r][c].isEmpty()) row.createCell(c).setCellValue(rows[r][c]);
                }
            }
            wb.write(bos);
        }
        return new MockMultipartFile("file", "students.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bos.toByteArray());
    }

    @Test
    @Order(16)
    void testImportStudentsByColumnName() throws Exception {
        // 表头含"序号"列且列顺序不同：应按列名匹配，序号不得被当学号
        MultipartFile file = buildXlsx(
                new String[]{"序号", "学号", "姓名", "电话", "邮箱"},
                new String[][]{
                        {"1", "S_col_1", "列匹配学生1", "13811112222", "a@x.com"},
                        {"2", "S_col_2", "列匹配学生2", "13833334444", "b@x.com"},
                });
        Map<String, Object> result = userService.importStudentsFromExcel(1L, file);
        assertEquals(2, result.get("imported"), "应按列名正确识别学号/姓名/电话/邮箱");
        assertEquals(0, ((List<?>) result.get("errors")).size());
        User s1 = userService.getOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getUsername, "S_col_1"));
        assertNotNull(s1);
        assertEquals("列匹配学生1", s1.getRealName());
        assertEquals("13811112222", s1.getPhone());
        assertEquals("a@x.com", s1.getEmail());
    }

    @Test
    @Order(17)
    void testImportStudentsRejectOnError() throws Exception {
        // 含 3 类错误：学号为空 / 表格内重复 / 库中已存在 → 整表不导入
        MultipartFile file = buildXlsx(
                new String[]{"学号", "姓名"},
                new String[][]{
                        {"", "无名氏"},                    // 学号为空
                        {"S_dup_x", "重复甲"},             // 表格内重复（下面再出现一次）
                        {"S_dup_x", "重复乙"},
                        {"20240001", "张三已在库"},        // 与初始数据重复
                        {"S_ok_x", "正常学生"},            // 正确行，但整表有错 → 也不导入
                });
        Map<String, Object> result = userService.importStudentsFromExcel(1L, file);
        assertEquals(0, result.get("imported"), "存在错误时整表不导入");
        List<?> errors = (List<?>) result.get("errors");
        assertEquals(3, errors.size()); // 学号为空 + 表格内重复 + 库中已存在
        // 正确行不落库
        assertNull(userService.getOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getUsername, "S_ok_x")));
    }

    @Test
    @Order(18)
    void testImportStudentsMissingHeader() throws Exception {
        MultipartFile file = buildXlsx(new String[]{"序号", "姓名"}, new String[][]{{"1", "缺学号列"}});
        Map<String, Object> result = userService.importStudentsFromExcel(1L, file);
        assertEquals(0, result.get("imported"));
        List<?> errors = (List<?>) result.get("errors");
        assertEquals(1, errors.size());
        assertTrue(((Map<?, ?>) errors.get(0)).get("msg").toString().contains("学号"));
    }
}
