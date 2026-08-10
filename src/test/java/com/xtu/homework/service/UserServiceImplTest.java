package com.xtu.homework.service;

import com.xtu.homework.HomeworkApplication;
import com.xtu.homework.dao.HomeworkDao;
import com.xtu.homework.dao.SubmissionDao;
import com.xtu.homework.dao.TeachingClassClazzDao;
import com.xtu.homework.dao.VerificationCodeDao;
import com.xtu.homework.entity.Homework;
import com.xtu.homework.entity.Submission;
import com.xtu.homework.entity.TeachingClassClazz;
import com.xtu.homework.entity.User;
import com.xtu.homework.entity.VerificationCode;
import com.xtu.homework.service.VerificationCodeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    @Autowired
    private VerificationCodeService verificationCodeService;

    @Autowired
    private VerificationCodeDao verificationCodeDao;

    @Autowired
    private HomeworkDao homeworkDao;

    @Autowired
    private SubmissionDao submissionDao;

    @Autowired
    private TeachingClassClazzDao teachingClassClazzDao;

    @Autowired
    private HomeworkService homeworkService;

    @Autowired
    private SubmissionService submissionService;

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

    @Test
    @Order(15)
    void testDeleteNonexistentStudent() {
        assertThrows(RuntimeException.class, () -> userService.deleteStudent(99999L));
    }

    // ========== 个人中心（updateProfile）==========

    @Test
    @Order(19)
    void testUpdateProfile() {
        // 用初始学生 20240001（张三）改资料
        User stu = userService.getOne(new LambdaQueryWrapper<User>().eq(User::getUsername, "20240001"));
        assertNotNull(stu);
        userService.updateProfile(stu.getId(), "张三丰", "13800138000", "zhangsan@xtu.edu.cn");
        User after = userService.getById(stu.getId());
        assertEquals("张三丰", after.getRealName());
        assertEquals("13800138000", after.getPhone());
        assertEquals("zhangsan@xtu.edu.cn", after.getEmail());
        // 还原，避免影响其他用例
        userService.updateProfile(stu.getId(), "张三", null, null);
    }

    @Test
    @Order(20)
    void testUpdateProfileBlankNameRejected() {
        User stu = userService.getOne(new LambdaQueryWrapper<User>().eq(User::getUsername, "20240001"));
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.updateProfile(stu.getId(), "  ", "1", null));
        assertTrue(ex.getMessage().contains("姓名"));
    }

    @Test
    @Order(21)
    void testUpdateProfileNonexistentUser() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.updateProfile(999999L, "x", null, null));
        assertTrue(ex.getMessage().contains("不存在"));
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
        // 初始密码统一为 Admin123456（用户确认的功能模型），且要求首次登录重置
        assertTrue(saved.getPwdResetRequired());
        assertTrue(saved.getPassword().startsWith("$2"));
        assertTrue(userService.login(saved.getUsername(), "Admin123456") != null);
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
        assertNull(after, "删除应为物理删除，账号应彻底不存在");
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

    // ========== 邮箱注册（registerByEmail）==========

    @Test
    @Order(30)
    void testRegisterByEmailSuccess() {
        String email = "reg_test_" + System.currentTimeMillis() + "@test.com";
        String code = verificationCodeService.issue("email", email);
        userService.registerByEmail("reguser", email, "RegUser123", code);
        User u = userService.getOne(new LambdaQueryWrapper<User>().eq(User::getUsername, "reguser"));
        assertNotNull(u, "注册用户应存在");
        assertEquals("STUDENT", u.getRole(), "注册角色应为 STUDENT");
        assertEquals(email, u.getEmail());
        assertFalse(u.getPwdResetRequired() != null && u.getPwdResetRequired(), "注册用户不应要求重置密码");
        // 验证码已标记使用：同码再注册应失败
        assertThrows(RuntimeException.class, () ->
                userService.registerByEmail("reguser2", email, "RegUser456", code));
    }

    @Test
    @Order(31)
    void testRegisterByEmailWrongCodeRejected() {
        assertThrows(RuntimeException.class, () ->
                userService.registerByEmail("reguser3", "wrong_code_" + System.currentTimeMillis() + "@test.com", "RegUser123", "000000"));
    }

    @Test
    @Order(32)
    void testRegisterByEmailDuplicateUsername() {
        String email = "dup_name_" + System.currentTimeMillis() + "@test.com";
        String code = verificationCodeService.issue("email", email);
        userService.registerByEmail("dupuser", email, "RegUser123", code);
        String email2 = "dup_name2_" + System.currentTimeMillis() + "@test.com";
        String code2 = verificationCodeService.issue("email", email2);
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                userService.registerByEmail("dupuser", email2, "RegUser456", code2));
        assertTrue(ex.getMessage().contains("已存在"));
    }

    @Test
    @Order(33)
    void testRegisterByEmailDuplicateEmail() {
        String email = "dup_email_" + System.currentTimeMillis() + "@test.com";
        String code = verificationCodeService.issue("email", email);
        userService.registerByEmail("dupemail1", email, "RegUser123", code);
        // 同邮箱再次注册：重置验证码 used_at 复用（避免 60s 冷却干扰），应报"邮箱已注册"
        VerificationCode vc = verificationCodeDao.selectOne(
                new LambdaQueryWrapper<VerificationCode>()
                        .eq(VerificationCode::getTarget, email)
                        .orderByDesc(VerificationCode::getCreatedAt)
                        .last("LIMIT 1"));
        // updateById 默认忽略 null 字段，用 UpdateWrapper 显式置空 used_at
        verificationCodeDao.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<VerificationCode>()
                .eq(VerificationCode::getId, vc.getId())
                .set(VerificationCode::getUsedAt, null));
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                userService.registerByEmail("dupemail2", email, "RegUser456", code));
        assertTrue(ex.getMessage().contains("邮箱已注册"));
    }

    @Test
    @Order(34)
    void testRegisterByEmailWeakPasswordRejected() {
        String email = "weak_pwd_" + System.currentTimeMillis() + "@test.com";
        String code = verificationCodeService.issue("email", email);
        assertThrows(RuntimeException.class, () ->
                userService.registerByEmail("weakpwd", email, "weak", code));
    }

    @Test
    @Order(35)
    void testRegisterByEmailInvalidUsernameRejected() {
        String email = "bad_name_" + System.currentTimeMillis() + "@test.com";
        String code = verificationCodeService.issue("email", email);
        assertThrows(RuntimeException.class, () ->
                userService.registerByEmail("ab", email, "RegUser123", code));
    }

    // ========== 学生自动加入教学班（新同学看到同班已发布作业）==========

    @Test
    @Order(36)
    void testStudentAutoJoinsTeachingClassSubmissions() {
        // 自插一个已发布作业到教学班1（H2 初始：教学班1 关联自然班1、2）
        Homework hw = new Homework();
        hw.setTitle("自动入班测试作业-" + System.currentTimeMillis());
        hw.setTeachingClassId(1L);
        hw.setTeacherId(2L);
        hw.setDeadline(LocalDateTime.now().plusDays(7));
        hw.setTotalScore(BigDecimal.TEN);
        hw.setStatus("PUBLISHED");
        hw.setQuestionLocked(false);
        homeworkDao.insert(hw);

        // 新增学生到自然班1 → 应自动补上教学班1已发布作业的提交记录（新同学立即可见同班作业）
        User s = new User();
        s.setUsername("S_autojoin_" + System.currentTimeMillis());
        s.setRealName("自动入班学生");
        User saved = userService.addStudent(1L, s);
        Long cnt = submissionDao.selectCount(
                new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getHomeworkId, hw.getId())
                        .eq(Submission::getStudentId, saved.getId()));
        assertEquals(1L, cnt, "新增学生应自动获得自然班关联教学班已发布作业的提交记录");

        // 幂等：重复同步不产生重复记录
        userService.syncStudentToTeachingClasses(saved.getId(), 1L);
        Long cnt2 = submissionDao.selectCount(
                new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getHomeworkId, hw.getId())
                        .eq(Submission::getStudentId, saved.getId()));
        assertEquals(1L, cnt2, "重复同步应幂等（不重复插入）");

        // 转班场景：建一个只关联自然班2的教学班及其作业，学生从班1转到班2 → 应补上该作业
        TeachingClassClazz link = new TeachingClassClazz();
        link.setTeachingClassId(999L);
        link.setClazzId(2L);
        teachingClassClazzDao.insert(link);
        Homework hw2 = new Homework();
        hw2.setTitle("转班自动入班测试-" + System.currentTimeMillis());
        hw2.setTeachingClassId(999L);
        hw2.setTeacherId(2L);
        hw2.setDeadline(LocalDateTime.now().plusDays(7));
        hw2.setTotalScore(BigDecimal.TEN);
        hw2.setStatus("PUBLISHED");
        hw2.setQuestionLocked(false);
        homeworkDao.insert(hw2);

        userService.transferStudent(saved.getId(), 1L, 2L);
        Long cnt3 = submissionDao.selectCount(
                new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getHomeworkId, hw2.getId())
                        .eq(Submission::getStudentId, saved.getId()));
        assertEquals(1L, cnt3, "转班学生应自动获得新自然班关联教学班已发布作业的提交记录");

        // 旧班历史 submission 保留，但学生端权限必须按当前自然班动态计算。
        TeachingClassClazz oldLink = new TeachingClassClazz();
        oldLink.setTeachingClassId(998L);
        oldLink.setClazzId(1L);
        teachingClassClazzDao.insert(oldLink);
        Homework oldHw = new Homework();
        oldHw.setTitle("转班后不可见的旧班作业-" + System.currentTimeMillis());
        oldHw.setTeachingClassId(998L);
        oldHw.setTeacherId(2L);
        oldHw.setDeadline(LocalDateTime.now().plusDays(7));
        oldHw.setTotalScore(BigDecimal.TEN);
        oldHw.setStatus("PUBLISHED");
        oldHw.setQuestionLocked(false);
        homeworkDao.insert(oldHw);
        Submission oldSub = new Submission();
        oldSub.setHomeworkId(oldHw.getId());
        oldSub.setStudentId(saved.getId());
        oldSub.setStatus("NOT_SUBMITTED");
        submissionDao.insert(oldSub);

        var currentPage = homeworkService.listStudentHomeworks(saved.getId(), 1, 100);
        assertFalse(currentPage.getRecords().stream().anyMatch(h -> h.getId().equals(oldHw.getId())),
                "转班后旧教学班作业不应继续出现在学生列表");
        assertTrue(currentPage.getRecords().stream().anyMatch(h -> h.getId().equals(hw2.getId())),
                "转班后新教学班作业应立即可见");
        RuntimeException detailError = assertThrows(RuntimeException.class,
                () -> homeworkService.getHomeworkDetail(oldHw.getId(), saved.getId()));
        assertTrue(detailError.getMessage().contains("不属于当前教学班"));

        com.xtu.homework.dto.SubmissionDto submitDto = new com.xtu.homework.dto.SubmissionDto();
        submitDto.setHomeworkId(oldHw.getId());
        submitDto.setAnswers(List.of());
        RuntimeException submitError = assertThrows(RuntimeException.class,
                () -> submissionService.submit(saved.getId(), submitDto));
        assertTrue(submitError.getMessage().contains("不属于当前教学班"));
    }
}
