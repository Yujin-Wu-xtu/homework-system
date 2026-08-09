package com.xtu.homework.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;

/**
 * 数据库初始化器
 * - Schema: MySQL/MariaDB → init.sql（仅 DDL + 幂等初始账号）；非 MySQL 环境跳过
 *   （测试用 H2 内存库由测试配置 spring.sql.init 建表灌数据，不依赖本类）
 * - Data: 使用 Java 硬编码插入（避免 Windows GBK 编码导致 SQL 文件中文乱码）
 * - 幂等：sys_user 已有数据时跳过 Java 数据初始化（MySQL 持久库重启不重复插入）
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public DataInitializer(DataSource ds) {
        this.jdbc = new JdbcTemplate(ds);
        this.dataSource = ds;
    }

    @Override
    public void run(String... args) {
        try {
            initSchema();
            if (!hasData()) {
                initData();
            }
            System.out.println("[OK] Database initialized with UTF-8 data");
        } catch (Exception e) {
            System.err.println("[WARN] DB init: " + e.getMessage());
        }
    }

    /** 运行环境只用 MySQL：返回 init.sql；非 MySQL（测试 H2）返回 null 跳过 schema 初始化 */
    private String resolveSchemaFile() {
        try (Connection conn = dataSource.getConnection()) {
            String driver = conn.getMetaData().getDriverName().toLowerCase();
            if (driver.contains("mysql") || driver.contains("mariadb")) {
                return "sql/init.sql";
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** 幂等检查：是否已完成完整初始化（以初始学生数据为标志，而非仅 admin 账号） */
    private boolean hasData() {
        try {
            Long count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_user WHERE role = 'STUDENT'", Long.class);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void initSchema() {
        String schemaFile = resolveSchemaFile();
        if (schemaFile == null) {
            // 非 MySQL 环境（测试 H2）：表由测试配置 spring.sql.init 建好，跳过
            return;
        }
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new ClassPathResource(schemaFile).getInputStream(),
                        StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("--")) continue;
                sb.append(line).append("\n");
                if (t.endsWith(";")) {
                    jdbc.execute(sb.toString().trim());
                    sb.setLength(0);
                }
            }
            String rest = sb.toString().trim();
            if (!rest.isEmpty()) jdbc.execute(rest);
            System.out.println("[OK] Schema init: " + schemaFile);
        } catch (Exception e) {
            System.err.println("[WARN] Schema init: " + e.getMessage());
        }
    }

    private void initData() {
        // Admin (password: Admin123456) — init.sql 已幂等预置，存在则跳过
        Long adminId = getUserId("admin");
        if (adminId == null) {
            insertUser("admin", "系统管理员", "ADMIN", null);
            adminId = getUserId("admin");
        }

        // Teacher
        Long teacherId = getUserId("T2024001");
        if (teacherId == null) {
            insertUser("T2024001", "张老师", "TEACHER", null);
            teacherId = getUserId("T2024001");
        }

        // Classes
        Long c1 = getClazzId("2024级软件工程1班");
        if (c1 == null) {
            jdbc.update("INSERT INTO clazz (name, grade, major) VALUES (?, ?, ?)",
                    "2024级软件工程1班", "2024级", "软件工程");
            c1 = getClazzId("2024级软件工程1班");
        }
        Long c2 = getClazzId("2024级计算机科学1班");
        if (c2 == null) {
            jdbc.update("INSERT INTO clazz (name, grade, major) VALUES (?, ?, ?)",
                    "2024级计算机科学1班", "2024级", "计算机科学与技术");
            c2 = getClazzId("2024级计算机科学1班");
        }

        // Students
        String[] students = {
                "20240001,张三," + c1, "20240002,李四," + c1, "20240003,王五," + c1,
                "20240004,赵六," + c2, "20240005,钱七," + c2, "20240006,孙八," + c2
        };
        for (String s : students) {
            String[] p = s.split(",");
            if (getUserId(p[0]) == null) {
                insertUser(p[0], p[1], "STUDENT", Long.parseLong(p[2]));
            }
        }

        // Teaching class（引用真实 teacherId）
        Long tcId = jdbc.query("SELECT id FROM teaching_class WHERE name = ?",
                rs -> rs.next() ? rs.getLong(1) : null, "2024级数据结构教学班");
        if (tcId == null) {
            jdbc.update("INSERT INTO teaching_class (name, teacher_id) VALUES (?, ?)",
                    "2024级数据结构教学班", teacherId);
            tcId = jdbc.query("SELECT id FROM teaching_class WHERE name = ?",
                    rs -> rs.next() ? rs.getLong(1) : null, "2024级数据结构教学班");
        }
        jdbc.update("INSERT IGNORE INTO teaching_class_clazz (teaching_class_id, clazz_id) VALUES (?, ?)", tcId, c1);
        jdbc.update("INSERT IGNORE INTO teaching_class_clazz (teaching_class_id, clazz_id) VALUES (?, ?)", tcId, c2);

        // Knowledge points（父子关系用真实 parentId）
        Long kpRoot = getKpId("数据结构");
        if (kpRoot == null) {
            jdbc.update("INSERT INTO knowledge_point (name, subject, parent_id, description) VALUES (?, ?, ?, ?)",
                    "数据结构", "计算机科学", null, "数据结构基础知识");
            kpRoot = getKpId("数据结构");
        }
        Long kpList = getKpId("线性表");
        if (kpList == null) {
            jdbc.update("INSERT INTO knowledge_point (name, subject, parent_id, description) VALUES (?, ?, ?, ?)",
                    "线性表", "计算机科学", kpRoot, "线性结构的基本类型");
            kpList = getKpId("线性表");
        }
        Long kpStack = getKpId("栈和队列");
        if (kpStack == null) {
            jdbc.update("INSERT INTO knowledge_point (name, subject, parent_id, description) VALUES (?, ?, ?, ?)",
                    "栈和队列", "计算机科学", kpRoot, "受限线性表");
            kpStack = getKpId("栈和队列");
        }

        // Questions（creatorId 用真实 adminId；选项/知识点关联用真实 id）
        Long q1 = insertQuestion("SINGLE_CHOICE", "以下哪种数据结构是线性结构？",
                "C", null, 5, "EASY", adminId);
        insertOption(q1, "A", "树", 1); insertOption(q1, "B", "图", 2);
        insertOption(q1, "C", "线性表", 3); insertOption(q1, "D", "集合", 4);
        insertQk(q1, kpList);

        Long q2 = insertQuestion("SINGLE_CHOICE", "在长度为n的线性表中查找一个元素，最坏情况下需要比较的次数是？",
                "D", null, 5, "MEDIUM", adminId);
        insertOption(q2, "A", "1", 1); insertOption(q2, "B", "log n", 2);
        insertOption(q2, "C", "n/2", 3); insertOption(q2, "D", "n", 4);
        insertQk(q2, kpList);

        Long q3 = insertQuestion("TRUE_FALSE", "栈是一种先进先出（FIFO）的数据结构。",
                "错", null, 5, "EASY", adminId);
        insertQk(q3, kpStack);

        Long q4 = insertQuestion("MULTI_CHOICE", "以下哪些属于排序算法？",
                "ACD", null, 10, "MEDIUM", adminId);
        insertOption(q4, "A", "冒泡排序", 1); insertOption(q4, "B", "广度优先遍历", 2);
        insertOption(q4, "C", "快速排序", 3); insertOption(q4, "D", "插入排序", 4);
        insertQk(q4, kpRoot);

        Long q5 = insertQuestion("ESSAY", "请简述栈和队列的主要区别，并分别举出一个实际应用场景。",
                null, "栈是后进先出(LIFO)，队列是先进先出(FIFO)。栈的应用：函数调用栈、表达式求值、括号匹配。队列的应用：打印队列、BFS遍历、消息队列。",
                15, "MEDIUM", adminId);
        insertQk(q5, kpStack);
    }

    // ---- 辅助方法：真实 ID 查询 + 幂等插入（兼容 H2/MySQL 自增差异）----

    private Long getUserId(String username) {
        return jdbc.query("SELECT id FROM sys_user WHERE username = ?",
                rs -> rs.next() ? rs.getLong(1) : null, username);
    }

    private void insertUser(String username, String realName, String role, Long clazzId) {
        String pwd = "$2b$10$Dputx5F0q3szQJLH1458i.bcUET76edCRA6jWPQFvfYAXq02jQnBq";
        if (clazzId == null) {
            jdbc.update("INSERT INTO sys_user (username, password, real_name, role, status, pwd_reset_required) " +
                    "VALUES (?, ?, ?, ?, 'ACTIVE', false)", username, pwd, realName, role);
        } else {
            jdbc.update("INSERT INTO sys_user (username, password, real_name, role, clazz_id, status, pwd_reset_required) " +
                    "VALUES (?, ?, ?, ?, ?, 'ACTIVE', false)", username, pwd, realName, role, clazzId);
        }
    }

    private Long getClazzId(String name) {
        return jdbc.query("SELECT id FROM clazz WHERE name = ?",
                rs -> rs.next() ? rs.getLong(1) : null, name);
    }

    private Long getKpId(String name) {
        return jdbc.query("SELECT id FROM knowledge_point WHERE name = ?",
                rs -> rs.next() ? rs.getLong(1) : null, name);
    }

    private Long insertQuestion(String type, String content, String correctAnswer,
                                String referenceAnswer, int score, String difficulty, Long creatorId) {
        Long exist = jdbc.query("SELECT id FROM question WHERE content = ?",
                rs -> rs.next() ? rs.getLong(1) : null, content);
        if (exist != null) return exist;
        if (correctAnswer == null) {
            jdbc.update("INSERT INTO question (type, content, reference_answer, score, difficulty, status, creator_id) " +
                    "VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?)",
                    type, content, referenceAnswer, score, difficulty, creatorId);
        } else {
            jdbc.update("INSERT INTO question (type, content, correct_answer, score, difficulty, status, creator_id) " +
                    "VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?)",
                    type, content, correctAnswer, score, difficulty, creatorId);
        }
        return jdbc.query("SELECT id FROM question WHERE content = ?",
                rs -> rs.next() ? rs.getLong(1) : null, content);
    }

    private void insertOption(Long questionId, String label, String content, int sortOrder) {
        jdbc.update("INSERT INTO question_option (question_id, label, content, sort_order) VALUES (?, ?, ?, ?)",
                questionId, label, content, sortOrder);
    }

    private void insertQk(Long questionId, Long kpId) {
        jdbc.update("INSERT INTO question_knowledge (question_id, knowledge_point_id) VALUES (?, ?)",
                questionId, kpId);
    }
}
