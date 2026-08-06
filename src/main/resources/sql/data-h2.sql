-- =====================================================
-- H2 数据库初始测试数据
-- 所有密码: Admin123456  (BCrypt hash)
-- =====================================================

INSERT INTO sys_user (username, password, real_name, role, status, pwd_reset_required)
VALUES ('admin', '$2b$10$Dputx5F0q3szQJLH1458i.bcUET76edCRA6jWPQFvfYAXq02jQnBq', '系统管理员', 'ADMIN', 'ACTIVE', FALSE);

INSERT INTO sys_user (username, password, real_name, role, status, pwd_reset_required)
VALUES ('T2024001', '$2b$10$Dputx5F0q3szQJLH1458i.bcUET76edCRA6jWPQFvfYAXq02jQnBq', '张老师', 'TEACHER', 'ACTIVE', FALSE);

INSERT INTO clazz (name, grade, major) VALUES ('2024级软件工程1班', '2024级', '软件工程');
INSERT INTO clazz (name, grade, major) VALUES ('2024级计算机科学1班', '2024级', '计算机科学与技术');

INSERT INTO sys_user (username, password, real_name, role, clazz_id, status, pwd_reset_required)
VALUES ('20240001', '$2b$10$Dputx5F0q3szQJLH1458i.bcUET76edCRA6jWPQFvfYAXq02jQnBq', '张三', 'STUDENT', 1, 'ACTIVE', FALSE);
INSERT INTO sys_user (username, password, real_name, role, clazz_id, status, pwd_reset_required)
VALUES ('20240002', '$2b$10$Dputx5F0q3szQJLH1458i.bcUET76edCRA6jWPQFvfYAXq02jQnBq', '李四', 'STUDENT', 1, 'ACTIVE', FALSE);
INSERT INTO sys_user (username, password, real_name, role, clazz_id, status, pwd_reset_required)
VALUES ('20240003', '$2b$10$Dputx5F0q3szQJLH1458i.bcUET76edCRA6jWPQFvfYAXq02jQnBq', '王五', 'STUDENT', 1, 'ACTIVE', FALSE);
INSERT INTO sys_user (username, password, real_name, role, clazz_id, status, pwd_reset_required)
VALUES ('20240004', '$2b$10$Dputx5F0q3szQJLH1458i.bcUET76edCRA6jWPQFvfYAXq02jQnBq', '赵六', 'STUDENT', 2, 'ACTIVE', FALSE);
INSERT INTO sys_user (username, password, real_name, role, clazz_id, status, pwd_reset_required)
VALUES ('20240005', '$2b$10$Dputx5F0q3szQJLH1458i.bcUET76edCRA6jWPQFvfYAXq02jQnBq', '钱七', 'STUDENT', 2, 'ACTIVE', FALSE);
INSERT INTO sys_user (username, password, real_name, role, clazz_id, status, pwd_reset_required)
VALUES ('20240006', '$2b$10$Dputx5F0q3szQJLH1458i.bcUET76edCRA6jWPQFvfYAXq02jQnBq', '孙八', 'STUDENT', 2, 'ACTIVE', FALSE);

INSERT INTO teaching_class (name, teacher_id) VALUES ('2024级数据结构教学班', 2);
INSERT INTO teaching_class_clazz (teaching_class_id, clazz_id) VALUES (1, 1);
INSERT INTO teaching_class_clazz (teaching_class_id, clazz_id) VALUES (1, 2);

INSERT INTO knowledge_point (name, subject, parent_id, description) VALUES ('数据结构', '计算机科学', NULL, '数据结构基础知识');
INSERT INTO knowledge_point (name, subject, parent_id, description) VALUES ('线性表', '计算机科学', 1, '线性结构的基本类型');
INSERT INTO knowledge_point (name, subject, parent_id, description) VALUES ('栈和队列', '计算机科学', 1, '受限线性表');

INSERT INTO question (id, type, content, correct_answer, score, difficulty, status, creator_id)
VALUES (1, 'SINGLE_CHOICE', '以下哪种数据结构是线性结构？', 'C', 5, 'EASY', 'ACTIVE', 1);
INSERT INTO question_option (question_id, label, content, sort_order) VALUES (1, 'A', '树', 1);
INSERT INTO question_option (question_id, label, content, sort_order) VALUES (1, 'B', '图', 2);
INSERT INTO question_option (question_id, label, content, sort_order) VALUES (1, 'C', '线性表', 3);
INSERT INTO question_option (question_id, label, content, sort_order) VALUES (1, 'D', '集合', 4);
INSERT INTO question_knowledge (question_id, knowledge_point_id) VALUES (1, 2);

INSERT INTO question (id, type, content, correct_answer, score, difficulty, status, creator_id)
VALUES (2, 'SINGLE_CHOICE', '在长度为n的线性表中查找一个元素，最坏情况下需要比较的次数是？', 'D', 5, 'MEDIUM', 'ACTIVE', 1);
INSERT INTO question_option (question_id, label, content, sort_order) VALUES (2, 'A', '1', 1);
INSERT INTO question_option (question_id, label, content, sort_order) VALUES (2, 'B', 'log n', 2);
INSERT INTO question_option (question_id, label, content, sort_order) VALUES (2, 'C', 'n/2', 3);
INSERT INTO question_option (question_id, label, content, sort_order) VALUES (2, 'D', 'n', 4);
INSERT INTO question_knowledge (question_id, knowledge_point_id) VALUES (2, 2);

INSERT INTO question (id, type, content, correct_answer, score, difficulty, status, creator_id)
VALUES (3, 'TRUE_FALSE', '栈是一种先进先出（FIFO）的数据结构。', '错', 5, 'EASY', 'ACTIVE', 1);
INSERT INTO question_knowledge (question_id, knowledge_point_id) VALUES (3, 3);

INSERT INTO question (id, type, content, correct_answer, score, difficulty, status, creator_id)
VALUES (4, 'MULTI_CHOICE', '以下哪些属于排序算法？', 'ACD', 10, 'MEDIUM', 'ACTIVE', 1);
INSERT INTO question_option (question_id, label, content, sort_order) VALUES (4, 'A', '冒泡排序', 1);
INSERT INTO question_option (question_id, label, content, sort_order) VALUES (4, 'B', '广度优先遍历', 2);
INSERT INTO question_option (question_id, label, content, sort_order) VALUES (4, 'C', '快速排序', 3);
INSERT INTO question_option (question_id, label, content, sort_order) VALUES (4, 'D', '插入排序', 4);
INSERT INTO question_knowledge (question_id, knowledge_point_id) VALUES (4, 1);

INSERT INTO question (id, type, content, reference_answer, score, difficulty, status, creator_id)
VALUES (5, 'ESSAY', '请简述栈和队列的主要区别，并分别举出一个实际应用场景。', '栈是后进先出(LIFO)，队列是先进先出(FIFO)。栈的应用：函数调用栈、表达式求值、括号匹配。队列的应用：打印队列、BFS遍历、消息队列。', 15, 'MEDIUM', 'ACTIVE', 1);
INSERT INTO question_knowledge (question_id, knowledge_point_id) VALUES (5, 3);
