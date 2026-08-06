package com.xtu.homework.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 数据库初始化器
 * - Schema: 从 SQL 文件读取（仅 DDL，无中文）
 * - Data: 使用 Java 硬编码插入（避免 Windows GBK 编码导致 SQL 文件中文乱码）
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbc;

    public DataInitializer(DataSource ds) {
        this.jdbc = new JdbcTemplate(ds);
    }

    @Override
    public void run(String... args) {
        try {
            initSchema();
            initData();
            System.out.println("[OK] Database initialized with UTF-8 data");
        } catch (Exception e) {
            System.err.println("[WARN] DB init: " + e.getMessage());
        }
    }

    private void initSchema() {
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new ClassPathResource("sql/schema-h2.sql").getInputStream(),
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
        } catch (Exception e) {
            System.err.println("[WARN] Schema init: " + e.getMessage());
        }
    }

    private void initData() {
        // Admin (password: Admin123456)
        jdbc.update("INSERT INTO sys_user (username, password, real_name, role, status, pwd_reset_required) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
                "admin", "$2b$10$Dputx5F0q3szQJLH1458i.bcUET76edCRA6jWPQFvfYAXq02jQnBq",
                "系统管理员", "ADMIN", "ACTIVE", false);

        // Teacher
        jdbc.update("INSERT INTO sys_user (username, password, real_name, role, status, pwd_reset_required) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
                "T2024001", "$2b$10$Dputx5F0q3szQJLH1458i.bcUET76edCRA6jWPQFvfYAXq02jQnBq",
                "张老师", "TEACHER", "ACTIVE", false);

        // Classes
        jdbc.update("INSERT INTO clazz (name, grade, major) VALUES (?, ?, ?)",
                "2024级软件工程1班", "2024级", "软件工程");
        jdbc.update("INSERT INTO clazz (name, grade, major) VALUES (?, ?, ?)",
                "2024级计算机科学1班", "2024级", "计算机科学与技术");

        // Students
        String[] students = {
                "20240001,张三,1", "20240002,李四,1", "20240003,王五,1",
                "20240004,赵六,2", "20240005,钱七,2", "20240006,孙八,2"
        };
        for (String s : students) {
            String[] p = s.split(",");
            jdbc.update("INSERT INTO sys_user (username, password, real_name, role, clazz_id, status, pwd_reset_required) " +
                    "VALUES (?, ?, ?, 'STUDENT', ?, 'ACTIVE', false)",
                    p[0], "$2b$10$Dputx5F0q3szQJLH1458i.bcUET76edCRA6jWPQFvfYAXq02jQnBq",
                    p[1], Long.parseLong(p[2]));
        }

        // Teaching class
        jdbc.update("INSERT INTO teaching_class (name, teacher_id) VALUES (?, ?)",
                "2024级数据结构教学班", 2L);
        jdbc.update("INSERT INTO teaching_class_clazz (teaching_class_id, clazz_id) VALUES (1, 1)");
        jdbc.update("INSERT INTO teaching_class_clazz (teaching_class_id, clazz_id) VALUES (1, 2)");

        // Knowledge points
        jdbc.update("INSERT INTO knowledge_point (name, subject, parent_id, description) VALUES (?, ?, ?, ?)",
                "数据结构", "计算机科学", null, "数据结构基础知识");
        jdbc.update("INSERT INTO knowledge_point (name, subject, parent_id, description) VALUES (?, ?, ?, ?)",
                "线性表", "计算机科学", 1L, "线性结构的基本类型");
        jdbc.update("INSERT INTO knowledge_point (name, subject, parent_id, description) VALUES (?, ?, ?, ?)",
                "栈和队列", "计算机科学", 1L, "受限线性表");

        // Questions
        jdbc.update("INSERT INTO question (id, type, content, correct_answer, score, difficulty, status, creator_id) " +
                "VALUES (1, 'SINGLE_CHOICE', ?, 'C', 5, 'EASY', 'ACTIVE', 1)",
                "以下哪种数据结构是线性结构？");
        jdbc.update("INSERT INTO question_option (question_id, label, content, sort_order) VALUES (1, 'A', '树', 1)");
        jdbc.update("INSERT INTO question_option (question_id, label, content, sort_order) VALUES (1, 'B', '图', 2)");
        jdbc.update("INSERT INTO question_option (question_id, label, content, sort_order) VALUES (1, 'C', '线性表', 3)");
        jdbc.update("INSERT INTO question_option (question_id, label, content, sort_order) VALUES (1, 'D', '集合', 4)");
        jdbc.update("INSERT INTO question_knowledge (question_id, knowledge_point_id) VALUES (1, 2)");

        jdbc.update("INSERT INTO question (id, type, content, correct_answer, score, difficulty, status, creator_id) " +
                "VALUES (2, 'SINGLE_CHOICE', ?, 'D', 5, 'MEDIUM', 'ACTIVE', 1)",
                "在长度为n的线性表中查找一个元素，最坏情况下需要比较的次数是？");
        jdbc.update("INSERT INTO question_option (question_id, label, content, sort_order) VALUES (2, 'A', '1', 1)");
        jdbc.update("INSERT INTO question_option (question_id, label, content, sort_order) VALUES (2, 'B', 'log n', 2)");
        jdbc.update("INSERT INTO question_option (question_id, label, content, sort_order) VALUES (2, 'C', 'n/2', 3)");
        jdbc.update("INSERT INTO question_option (question_id, label, content, sort_order) VALUES (2, 'D', 'n', 4)");
        jdbc.update("INSERT INTO question_knowledge (question_id, knowledge_point_id) VALUES (2, 2)");

        jdbc.update("INSERT INTO question (id, type, content, correct_answer, score, difficulty, status, creator_id) " +
                "VALUES (3, 'TRUE_FALSE', ?, '错', 5, 'EASY', 'ACTIVE', 1)",
                "栈是一种先进先出（FIFO）的数据结构。");
        jdbc.update("INSERT INTO question_knowledge (question_id, knowledge_point_id) VALUES (3, 3)");

        jdbc.update("INSERT INTO question (id, type, content, correct_answer, score, difficulty, status, creator_id) " +
                "VALUES (4, 'MULTI_CHOICE', ?, 'ACD', 10, 'MEDIUM', 'ACTIVE', 1)",
                "以下哪些属于排序算法？");
        jdbc.update("INSERT INTO question_option (question_id, label, content, sort_order) VALUES (4, 'A', '冒泡排序', 1)");
        jdbc.update("INSERT INTO question_option (question_id, label, content, sort_order) VALUES (4, 'B', '广度优先遍历', 2)");
        jdbc.update("INSERT INTO question_option (question_id, label, content, sort_order) VALUES (4, 'C', '快速排序', 3)");
        jdbc.update("INSERT INTO question_option (question_id, label, content, sort_order) VALUES (4, 'D', '插入排序', 4)");
        jdbc.update("INSERT INTO question_knowledge (question_id, knowledge_point_id) VALUES (4, 1)");

        jdbc.update("INSERT INTO question (id, type, content, reference_answer, score, difficulty, status, creator_id) " +
                "VALUES (5, 'ESSAY', ?, ?, 15, 'MEDIUM', 'ACTIVE', 1)",
                "请简述栈和队列的主要区别，并分别举出一个实际应用场景。",
                "栈是后进先出(LIFO)，队列是先进先出(FIFO)。栈的应用：函数调用栈、表达式求值、括号匹配。队列的应用：打印队列、BFS遍历、消息队列。");
        jdbc.update("INSERT INTO question_knowledge (question_id, knowledge_point_id) VALUES (5, 3)");
    }
}
