package com.xtu.homework.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xtu.homework.dao.AuditLogDao;
import com.xtu.homework.dao.ClazzDao;
import com.xtu.homework.dao.HomeworkDao;
import com.xtu.homework.dao.SubmissionDao;
import com.xtu.homework.dao.TeachingClassClazzDao;
import com.xtu.homework.dao.TeachingClassDao;
import com.xtu.homework.dao.TeachingClassStudentDao;
import com.xtu.homework.dao.UserDao;
import com.xtu.homework.entity.AuditLog;
import com.xtu.homework.entity.Clazz;
import com.xtu.homework.entity.Homework;
import com.xtu.homework.entity.Submission;
import com.xtu.homework.entity.TeachingClass;
import com.xtu.homework.entity.TeachingClassClazz;
import com.xtu.homework.entity.TeachingClassStudent;
import com.xtu.homework.entity.User;
import com.xtu.homework.service.UserService;
import com.xtu.homework.service.VerificationCodeService;
import com.xtu.homework.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户服务实现类
 * 负责登录认证、密码管理、教师/学生CRUD、Excel导入等核心业务逻辑
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserDao, User> implements UserService {

    /** 统一初始密码（管理员创建教师/导入创建学生时使用；首次登录强制修改） */
    public static final String DEFAULT_PASSWORD = "Admin123456";

    private final UserDao userDao;
    private final AuditLogDao auditLogDao;
    private final ClazzDao clazzDao;
    private final TeachingClassClazzDao teachingClassClazzDao;
    private final TeachingClassDao teachingClassDao;
    private final TeachingClassStudentDao teachingClassStudentDao;
    private final HomeworkDao homeworkDao;
    private final SubmissionDao submissionDao;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final VerificationCodeService verificationCodeService;
    private static final int MAX_LOGIN_FAILS = 5;
    private static final int LOCK_MINUTES = 30;

    @Override
    public String login(String username, String password) {
        User user = userDao.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            throw new RuntimeException("用户名或密码错误");
        }
        if ("DISABLED".equals(user.getStatus())) {
            throw new RuntimeException("账号已被禁用");
        }
        // 检查账号锁定状态
        if (user.getLockedUntil() != null &&
                LocalDateTime.now().isBefore(user.getLockedUntil())) {
            throw new RuntimeException("账号已被锁定，请" + LOCK_MINUTES + "分钟后再试");
        }
        // BCrypt 密码验证
        if (!passwordEncoder.matches(password, user.getPassword())) {
            int failCount = (user.getLoginFailCount() == null ? 0 : user.getLoginFailCount()) + 1;
            user.setLoginFailCount(failCount);
            if (failCount >= MAX_LOGIN_FAILS) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
                user.setLoginFailCount(0);
                userDao.updateById(user);
                throw new RuntimeException("密码错误次数过多，账号已锁定" + LOCK_MINUTES + "分钟");
            }
            userDao.updateById(user);
            throw new RuntimeException("用户名或密码错误");
        }
        // 登录成功，重置失败计数和锁定状态
        user.setLoginFailCount(0);
        user.setLockedUntil(null);
        userDao.updateById(user);
        // 记录审计日志
        AuditLog log = new AuditLog();
        log.setUserId(user.getId());
        log.setUsername(user.getUsername());
        log.setOperation("LOGIN");
        log.setTargetType("USER");
        log.setTargetId(String.valueOf(user.getId()));
        log.setDetail("用户登录成功");
        auditLogDao.insert(log);
        return jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
    }

    @Override
    public void changePassword(Long userId, String oldPwd, String newPwd) {
        User user = userDao.selectById(userId);
        if (!passwordEncoder.matches(oldPwd, user.getPassword())) {
            throw new RuntimeException("原密码错误");
        }
        // 密码复杂度校验: 至少8位，包含大小写字母和数字
        if (!newPwd.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$")) {
            throw new RuntimeException("密码需至少8位，包含大小写字母和数字");
        }
        user.setPassword(passwordEncoder.encode(newPwd));
        user.setPwdResetRequired(false);
        userDao.updateById(user);
    }

    @Override
    @Transactional
    public void registerByEmail(String username, String email, String password, String code) {
        // 1. 校验邮箱验证码（错误/过期/已用都会抛异常）
        verificationCodeService.verify("email", email, code);
        // 2. 用户名格式与唯一性
        String uname = username == null ? "" : username.trim();
        if (!uname.matches("^[a-zA-Z0-9_]{3,20}$")) {
            throw new RuntimeException("用户名需为 3-20 位字母、数字或下划线");
        }
        Long nameCount = userDao.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, uname));
        if (nameCount != null && nameCount > 0) {
            throw new RuntimeException("用户名已存在");
        }
        // 3. 邮箱唯一性
        String mail = email == null ? "" : email.trim();
        Long emailCount = userDao.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getEmail, mail));
        if (emailCount != null && emailCount > 0) {
            throw new RuntimeException("该邮箱已注册");
        }
        // 4. 密码复杂度校验（与改密码一致）
        if (password == null || !password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$")) {
            throw new RuntimeException("密码需至少8位，包含大小写字母和数字");
        }
        // 5. 创建 STUDENT 账号（班级由管理员后续分配，默认姓名=用户名）
        User user = new User();
        user.setUsername(uname);
        user.setPassword(passwordEncoder.encode(password));
        user.setRealName(uname);
        user.setEmail(mail);
        user.setRole("STUDENT");
        user.setStatus("ACTIVE");
        user.setPwdResetRequired(false);
        userDao.insert(user);
    }

    @Override
    public void updateProfile(Long userId, String realName, String phone, String email) {
        User user = userDao.selectById(userId);
        if (user == null) throw new RuntimeException("用户不存在");
        if (realName == null || realName.isBlank()) throw new RuntimeException("姓名不能为空");
        user.setRealName(realName.trim());
        user.setPhone(phone == null || phone.isBlank() ? null : phone.trim());
        user.setEmail(email == null || email.isBlank() ? null : email.trim());
        userDao.updateById(user);
    }

    @Override
    public String resetPassword(Long userId) {
        User user = userDao.selectById(userId);
        String newPwd = generateRandomPassword();
        user.setPassword(passwordEncoder.encode(newPwd));
        user.setPwdResetRequired(true);
        userDao.updateById(user);
        AuditLog al = new AuditLog();
        al.setUserId(userId);
        al.setUsername(user.getUsername());
        al.setOperation("RESET_PWD");
        al.setTargetType("USER");
        al.setTargetId(String.valueOf(userId));
        auditLogDao.insert(al);
        return newPwd;
    }

    @Override
    public Page<User> listTeachers(int page, int size, String keyword) {
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
        qw.eq(User::getRole, "TEACHER");
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(User::getUsername, keyword)
                          .or().like(User::getRealName, keyword));
        }
        return userDao.selectPage(new Page<>(page, size), qw);
    }

    @Override
    public User addTeacher(User teacher) {
        if (teacher.getUsername() == null || teacher.getUsername().isBlank()) {
            throw new RuntimeException("工号不能为空");
        }
        // 工号唯一性预查重（DB 有 UNIQUE 约束，预查重给出明确 400 而非数据库异常）
        User exist = userDao.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, teacher.getUsername()));
        if (exist != null) throw new RuntimeException("工号 " + teacher.getUsername() + " 已存在");
        teacher.setRole("TEACHER");
        teacher.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        teacher.setPwdResetRequired(true);
        teacher.setStatus("ACTIVE");
        userDao.insert(teacher);
        return teacher;
    }

    @Override
    @Transactional
    public User addStudent(Long clazzId, User student) {
        Clazz clazz = clazzDao.selectById(clazzId);
        if (clazz == null) throw new RuntimeException("班级不存在");
        if (student.getUsername() == null || student.getUsername().isBlank()) {
            throw new RuntimeException("学号不能为空");
        }
        User exist = userDao.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, student.getUsername()));
        if (exist != null) throw new RuntimeException("学号 " + student.getUsername() + " 已存在");
        student.setRole("STUDENT");
        student.setClazzId(clazzId);
        student.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        student.setPwdResetRequired(true);
        student.setStatus("ACTIVE");
        userDao.insert(student);
        // 学生进入自然班 → 自动补该班关联教学班已发布作业的提交记录（新同学立即可见同班作业）
        syncStudentToTeachingClasses(student.getId(), clazzId);
        return student;
    }

    /**
     * 学生进入自然班后，自动补齐该自然班关联教学班（teaching_class_clazz）中
     * 已发布/已关闭作业的 submission 记录（NOT_SUBMITTED）——学生端作业可见性按
     * submission 反查，补上后新同学/转班同学立刻能看到同班同学正在上的课程作业。
     * 幂等：已有 submission 的作业跳过。
     */
    @Override
    @Transactional
    public void syncStudentToTeachingClasses(Long studentId, Long clazzId) {
        if (studentId == null || clazzId == null) return;
        List<TeachingClassClazz> links = teachingClassClazzDao.selectList(
                new LambdaQueryWrapper<TeachingClassClazz>().eq(TeachingClassClazz::getClazzId, clazzId));
        if (links.isEmpty()) return;
        // 只自动同步必修教学班（专业课：学生进自然班即属于该班）；
        // 选修教学班的学生由教师手动拉入（addElectiveStudents → syncSubmissionForTeachingClass）
        List<Long> tcIds = links.stream()
                .map(TeachingClassClazz::getTeachingClassId).distinct()
                .filter(tcId -> {
                    TeachingClass tc = teachingClassDao.selectById(tcId);
                    return tc == null || !"ELECTIVE".equals(tc.getCourseType());
                })
                .toList();
        if (tcIds.isEmpty()) return;
        List<Homework> hws = homeworkDao.selectList(
                new LambdaQueryWrapper<Homework>()
                        .in(Homework::getTeachingClassId, tcIds)
                        .in(Homework::getStatus, List.of("PUBLISHED", "CLOSED")));
        for (Homework hw : hws) {
            Long cnt = submissionDao.selectCount(
                    new LambdaQueryWrapper<Submission>()
                            .eq(Submission::getHomeworkId, hw.getId())
                            .eq(Submission::getStudentId, studentId));
            if (cnt == null || cnt == 0) {
                Submission sub = new Submission();
                sub.setHomeworkId(hw.getId());
                sub.setStudentId(studentId);
                sub.setStatus("NOT_SUBMITTED");
                submissionDao.insert(sub);
            }
        }
    }

    /** 补学生在指定教学班已发布/已关闭作业的 submission（幂等）——必修(自然班动态)与选修(教师拉入)共用 */
    private void syncSubmissionForTeachingClass(Long studentId, Long tcId) {
        List<Homework> hws = homeworkDao.selectList(
                new LambdaQueryWrapper<Homework>()
                        .eq(Homework::getTeachingClassId, tcId)
                        .in(Homework::getStatus, List.of("PUBLISHED", "CLOSED")));
        for (Homework hw : hws) {
            Long cnt = submissionDao.selectCount(
                    new LambdaQueryWrapper<Submission>()
                            .eq(Submission::getHomeworkId, hw.getId())
                            .eq(Submission::getStudentId, studentId));
            if (cnt == null || cnt == 0) {
                Submission sub = new Submission();
                sub.setHomeworkId(hw.getId());
                sub.setStudentId(studentId);
                sub.setStatus("NOT_SUBMITTED");
                submissionDao.insert(sub);
            }
        }
    }

    // ========== 教学班学生（按课程类型分流）==========

    @Override
    public List<User> findTeachingClassStudents(Long tcId) {
        TeachingClass tc = teachingClassDao.selectById(tcId);
        if (tc == null) return List.of();
        if ("ELECTIVE".equals(tc.getCourseType())) {
            List<Long> sids = teachingClassStudentDao.selectList(
                            new LambdaQueryWrapper<TeachingClassStudent>()
                                    .eq(TeachingClassStudent::getTeachingClassId, tcId))
                    .stream().map(TeachingClassStudent::getStudentId).toList();
            return sids.isEmpty() ? List.of() : userDao.selectBatchIds(sids);
        }
        // 必修：学生的自然班级 ∈ 教学班关联班级（动态查询）
        return userDao.findStudentsByTeachingClassId(tcId);
    }

    @Override
    public long countTeachingClassStudents(Long tcId) {
        TeachingClass tc = teachingClassDao.selectById(tcId);
        if (tc == null) return 0;
        if ("ELECTIVE".equals(tc.getCourseType())) {
            return teachingClassStudentDao.selectCount(
                    new LambdaQueryWrapper<TeachingClassStudent>()
                            .eq(TeachingClassStudent::getTeachingClassId, tcId));
        }
        return userDao.countStudentsByTeachingClassId(tcId);
    }

    @Override
    @Transactional
    public int addElectiveStudents(Long tcId, List<Long> studentIds) {
        TeachingClass tc = teachingClassDao.selectById(tcId);
        if (tc == null) throw new RuntimeException("教学班不存在");
        if (!"ELECTIVE".equals(tc.getCourseType())) {
            throw new RuntimeException("仅选修教学班支持自由选择学生（必修教学班按自然班级拉取）");
        }
        int added = 0;
        for (Long sid : studentIds) {
            if (sid == null) continue;
            Long cnt = teachingClassStudentDao.selectCount(new LambdaQueryWrapper<TeachingClassStudent>()
                    .eq(TeachingClassStudent::getTeachingClassId, tcId)
                    .eq(TeachingClassStudent::getStudentId, sid));
            if (cnt == 0) {
                TeachingClassStudent tcs = new TeachingClassStudent();
                tcs.setTeachingClassId(tcId);
                tcs.setStudentId(sid);
                teachingClassStudentDao.insert(tcs);
                added++;
            }
            // 补该教学班已发布作业 submission → 学生端立即可见（模拟选课后进入课程）
            syncSubmissionForTeachingClass(sid, tcId);
        }
        return added;
    }

    @Override
    @Transactional
    public void removeElectiveStudent(Long tcId, Long studentId) {
        TeachingClassStudent tcs = teachingClassStudentDao.selectOne(new LambdaQueryWrapper<TeachingClassStudent>()
                .eq(TeachingClassStudent::getTeachingClassId, tcId)
                .eq(TeachingClassStudent::getStudentId, studentId));
        if (tcs != null) {
            teachingClassStudentDao.deleteById(tcs.getId());
        }
        // 历史 submission 保留（教师审计）；学生端访问由 StudentHomeworkAccessService 实时鉴权立即失效
    }

    @Override
    public void deleteStudent(Long studentId) {
        User student = userDao.selectById(studentId);
        if (student == null || !"STUDENT".equals(student.getRole())) {
            throw new RuntimeException("学生不存在");
        }
        // 软删除：置 DISABLED，保留历史提交记录的外键完整性
        student.setStatus("DISABLED");
        userDao.updateById(student);
    }

    @Override
    @Transactional
    public int importStudents(Long clazzId, List<User> students) {
        int count = 0;
        for (User s : students) {
            User exist = userDao.selectOne(
                    new LambdaQueryWrapper<User>().eq(User::getUsername, s.getUsername()));
            if (exist != null) continue;
            s.setRole("STUDENT");
            s.setClazzId(clazzId);
            // 统一初始密码（首次登录强制修改；账号用学号区分）
            s.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
            s.setPwdResetRequired(true);
            s.setStatus("ACTIVE");
            userDao.insert(s);
            // 学生进入自然班 → 自动补该班关联教学班已发布作业的提交记录
            syncStudentToTeachingClasses(s.getId(), clazzId);
            count++;
        }
        return count;
    }

    @Override
    @Transactional
    public void transferStudent(Long studentId, Long fromClazzId, Long toClazzId) {
        User student = userDao.selectById(studentId);
        student.setClazzId(toClazzId);
        userDao.updateById(student);
        // 转班 = 进入新自然班 → 自动补新班关联教学班已发布作业的提交记录。
        // 旧 submission 保留供教师审计，但学生端按当前自然班动态鉴权，不再可见或可提交旧班作业。
        syncStudentToTeachingClasses(studentId, toClazzId);
    }

    @Override
    public List<Clazz> importClasses(MultipartFile file) {
        List<Clazz> classes = new ArrayList<>();
        try (InputStream is = file.getInputStream();
             Workbook wb = new XSSFWorkbook(is)) {
            Sheet sheet = wb.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                Clazz c = new Clazz();
                // 列: 班级名称, 年级, 专业
                c.setName(getCellString(row.getCell(0)));
                c.setGrade(getCellString(row.getCell(1)));
                c.setMajor(getCellString(row.getCell(2)));
                if (c.getName() != null && !c.getName().isBlank()) {
                    Clazz exist = clazzDao.selectOne(
                            new LambdaQueryWrapper<Clazz>().eq(Clazz::getName, c.getName()));
                    if (exist == null) {
                        clazzDao.insert(c);
                        classes.add(c);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Excel解析失败: " + e.getMessage());
        }
        return classes;
    }

    @Override
    @Transactional
    public Map<String, Object> importStudentsFromExcel(Long clazzId, MultipartFile file) {
        Clazz clazz = clazzDao.selectById(clazzId);
        if (clazz == null) throw new RuntimeException("班级不存在");

        List<Map<String, Object>> errors = new ArrayList<>();
        List<User> toInsert = new ArrayList<>();
        try (InputStream is = file.getInputStream();
             Workbook wb = new XSSFWorkbook(is)) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet.getLastRowNum() < 1) {
                throw new RuntimeException("表格为空或只有表头，没有可导入的数据行");
            }

            // ---- 按表头列名匹配（避免固定列位置误匹配"序号"等列）----
            Row header = sheet.getRow(0);
            int colNo = -1, colName = -1, colPhone = -1, colEmail = -1;
            for (int c = 0; c < header.getLastCellNum(); c++) {
                String h = getCellString(header.getCell(c));
                if (h == null || h.isBlank()) continue;
                String hLower = h.toLowerCase();
                if (colNo == -1 && h.contains("学号")) colNo = c;
                else if (colName == -1 && h.contains("姓名")) colName = c;
                else if (colPhone == -1 && (h.contains("电话") || h.contains("手机"))) colPhone = c;
                else if (colEmail == -1 && (h.contains("邮箱") || hLower.contains("email"))) colEmail = c;
            }
            if (colNo == -1 || colName == -1) {
                errors.add(Map.of("row", 1, "msg",
                        "表头缺少必要列：需包含「学号」和「姓名」（表头位于第1行，请勿修改）"));
                return Map.of("imported", 0, "checked", 0, "errors", errors);
            }

            // ---- 逐行校验，收集全部错误（排错优先：有错误则整表不导入）----
            Set<String> seen = new HashSet<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String username = getCellString(row.getCell(colNo));
                String realName = getCellString(row.getCell(colName));
                String phone = colPhone >= 0 ? getCellString(row.getCell(colPhone)) : null;
                String email = colEmail >= 0 ? getCellString(row.getCell(colEmail)) : null;
                int rowNum = i + 1;

                if (username == null || username.isBlank()) {
                    errors.add(Map.of("row", rowNum, "msg", "学号为空"));
                    continue;
                }
                if (realName == null || realName.isBlank()) {
                    errors.add(Map.of("row", rowNum, "msg", "姓名为空"));
                    continue;
                }
                if (!seen.add(username)) {
                    errors.add(Map.of("row", rowNum, "msg", "表格内学号重复：" + username));
                    continue;
                }
                User exist = userDao.selectOne(
                        new LambdaQueryWrapper<User>().eq(User::getUsername, username));
                if (exist != null) {
                    errors.add(Map.of("row", rowNum, "msg", "学号已存在（" + exist.getRealName() + "）"));
                    continue;
                }

                User s = new User();
                s.setUsername(username);
                s.setRealName(realName);
                s.setRole("STUDENT");
                s.setClazzId(clazzId);
                s.setPhone(phone);
                s.setEmail(email);
                // 统一初始密码 Admin123456（3cac49f 功能模型：导入学生同新增学生；此处曾漏网用随机密码）
                s.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
                s.setPwdResetRequired(true);
                s.setStatus("ACTIVE");
                toInsert.add(s);
            }
        } catch (Exception e) {
            throw new RuntimeException("Excel解析失败: " + e.getMessage());
        }

        // 排错优先：只要存在错误行，整表不导入，由前端弹窗回显
        if (!errors.isEmpty()) {
            return Map.of("imported", 0, "checked", toInsert.size(), "errors", errors);
        }
        int count = 0;
        for (User s : toInsert) {
            userDao.insert(s);
            count++;
        }
        // 学生进入自然班 → 自动补该班关联教学班已发布作业的提交记录（批量导入的学生同样生效）
        for (User s : toInsert) {
            syncStudentToTeachingClasses(s.getId(), clazzId);
        }
        return Map.of("imported", count, "checked", toInsert.size(), "errors", errors);
    }

    private String getCellString(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }

    /** 管理员/教师重置密码时生成随机新密码（返回给调用方展示） */
    private String generateRandomPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        return random.ints(10, 0, chars.length())
                .mapToObj(chars::charAt)
                .map(Object::toString)
                .collect(Collectors.joining());
    }
}
