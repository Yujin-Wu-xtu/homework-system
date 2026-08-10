-- =====================================================
-- 在线作业系统 - MySQL 数据库初始化脚本
-- Database: homework_system
-- 表名与 H2 schema 保持一致 (sys_user 而非 user)
-- =====================================================

CREATE DATABASE IF NOT EXISTS homework_system
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
USE homework_system;

-- 用户表 (sys_user 避免 MySQL 关键字冲突)
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名(学号/工号)',
    password VARCHAR(200) NOT NULL COMMENT 'BCrypt哈希密码',
    real_name VARCHAR(50) NOT NULL COMMENT '真实姓名',
    role VARCHAR(20) NOT NULL COMMENT 'ADMIN/TEACHER/STUDENT',
    phone VARCHAR(20) COMMENT '手机号',
    email VARCHAR(100) COMMENT '电子邮箱',
    clazz_id BIGINT COMMENT '自然班级ID(学生)',
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    pwd_reset_required TINYINT NOT NULL DEFAULT 1 COMMENT '是否需修改密码',
    login_fail_count INT NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
    locked_until DATETIME COMMENT '锁定截止时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_role (role),
    INDEX idx_clazz_id (clazz_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE IF NOT EXISTS clazz (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE COMMENT '班级名称',
    grade VARCHAR(20) NOT NULL COMMENT '年级',
    major VARCHAR(100) NOT NULL COMMENT '专业',
    college VARCHAR(100) COMMENT '学院',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自然班级表';

CREATE TABLE IF NOT EXISTS teaching_class (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '教学班级名称',
    teacher_id BIGINT NOT NULL COMMENT '教师ID',
    course_type VARCHAR(20) NOT NULL DEFAULT 'REQUIRED' COMMENT '课程类型：REQUIRED=必修(专业课，按自然班拉学生) / ELECTIVE=选修(自由选学生)',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_teacher (teacher_id),
    FOREIGN KEY (teacher_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教学班级表';

CREATE TABLE IF NOT EXISTS teaching_class_clazz (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    teaching_class_id BIGINT NOT NULL,
    clazz_id BIGINT NOT NULL,
    INDEX idx_tc (teaching_class_id),
    INDEX idx_clazz (clazz_id),
    FOREIGN KEY (teaching_class_id) REFERENCES teaching_class(id),
    FOREIGN KEY (clazz_id) REFERENCES clazz(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教学班级-自然班级关联表（必修教学班用，学生动态查询）';

CREATE TABLE IF NOT EXISTS teaching_class_student (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    teaching_class_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    INDEX idx_tc (teaching_class_id),
    INDEX idx_student (student_id),
    FOREIGN KEY (teaching_class_id) REFERENCES teaching_class(id),
    FOREIGN KEY (student_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教学班级-学生关联表（选修教学班用，学生静态选择）';

CREATE TABLE IF NOT EXISTS knowledge_point (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '知识点名称',
    subject VARCHAR(100) COMMENT '所属学科',
    parent_id BIGINT COMMENT '父知识点ID',
    description VARCHAR(500) COMMENT '描述',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_subject (subject),
    INDEX idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识点表';

CREATE TABLE IF NOT EXISTS question (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(20) NOT NULL COMMENT 'SINGLE_CHOICE/MULTI_CHOICE/TRUE_FALSE/ESSAY',
    content TEXT NOT NULL COMMENT '题干',
    correct_answer VARCHAR(500) COMMENT '标准答案(客观题)',
    reference_answer TEXT COMMENT '参考答案(主观题)',
    score DECIMAL(5,2) NOT NULL DEFAULT 5.00 COMMENT '默认分值',
    difficulty VARCHAR(10) NOT NULL COMMENT 'EASY/MEDIUM/HARD',
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    tfidf_vector TEXT COMMENT 'TF-IDF向量(查重用)',
    creator_id BIGINT NOT NULL COMMENT '创建者ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FULLTEXT INDEX ft_content (content),
    INDEX idx_type_status (type, status),
    INDEX idx_difficulty (difficulty),
    FOREIGN KEY (creator_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目表';

CREATE TABLE IF NOT EXISTS question_option (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    question_id BIGINT NOT NULL,
    label VARCHAR(5) NOT NULL COMMENT 'A/B/C/D',
    content VARCHAR(1000) NOT NULL COMMENT '选项内容',
    sort_order INT NOT NULL COMMENT '排序号',
    INDEX idx_qid (question_id),
    FOREIGN KEY (question_id) REFERENCES question(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目选项表';

CREATE TABLE IF NOT EXISTS question_knowledge (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    question_id BIGINT NOT NULL,
    knowledge_point_id BIGINT NOT NULL,
    INDEX idx_qid (question_id),
    INDEX idx_kpid (knowledge_point_id),
    FOREIGN KEY (question_id) REFERENCES question(id),
    FOREIGN KEY (knowledge_point_id) REFERENCES knowledge_point(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目-知识点关联表';

CREATE TABLE IF NOT EXISTS homework (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL COMMENT '作业名称',
    description TEXT COMMENT '作业描述',
    teaching_class_id BIGINT NOT NULL,
    teacher_id BIGINT NOT NULL,
    deadline DATETIME NOT NULL COMMENT '提交截止时间',
    total_score DECIMAL(6,2) NOT NULL COMMENT '作业总分',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/CLOSED',
    question_locked TINYINT NOT NULL DEFAULT 0 COMMENT '题目是否锁定',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tc (teaching_class_id),
    INDEX idx_deadline (deadline),
    FOREIGN KEY (teacher_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作业表';

CREATE TABLE IF NOT EXISTS homework_question (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    homework_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    sort_order INT NOT NULL COMMENT '排序号',
    score DECIMAL(5,2) NOT NULL COMMENT '本题分值',
    INDEX idx_hw (homework_id),
    FOREIGN KEY (homework_id) REFERENCES homework(id),
    FOREIGN KEY (question_id) REFERENCES question(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作业-题目关联表';

CREATE TABLE IF NOT EXISTS submission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    homework_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NOT_SUBMITTED' COMMENT 'NOT_SUBMITTED/SUBMITTED/GRADED',
    open_time DATETIME COMMENT '首次打开时间',
    submit_time DATETIME COMMENT '提交时间',
    last_modified_time DATETIME COMMENT '最后修改时间',
    duration_seconds INT COMMENT '答题耗时(秒)',
    auto_score DECIMAL(6,2) COMMENT '客观题自动评分',
    manual_score DECIMAL(6,2) COMMENT '主观题人工评分',
    total_score DECIMAL(6,2) COMMENT '总分',
    suspicious_flag TINYINT DEFAULT 0 COMMENT '异常标记',
    UNIQUE KEY uk_hw_stu (homework_id, student_id),
    INDEX idx_student (student_id),
    FOREIGN KEY (homework_id) REFERENCES homework(id),
    FOREIGN KEY (student_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提交记录表';

CREATE TABLE IF NOT EXISTS submission_answer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    submission_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    student_answer TEXT COMMENT '学生答案',
    score DECIMAL(5,2) COMMENT '得分',
    comment VARCHAR(500) COMMENT '教师评语',
    graded_by BIGINT COMMENT '评分人ID',
    graded_time DATETIME COMMENT '评分时间',
    INDEX idx_sub (submission_id),
    FOREIGN KEY (submission_id) REFERENCES submission(id),
    FOREIGN KEY (question_id) REFERENCES question(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提交答案明细表';

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    username VARCHAR(50) NOT NULL,
    operation VARCHAR(50) NOT NULL COMMENT 'CREATE/UPDATE/DELETE/LOGIN',
    target_type VARCHAR(50) NOT NULL COMMENT 'USER/QUESTION/HOMEWORK',
    target_id VARCHAR(100) COMMENT '操作对象ID',
    detail TEXT COMMENT '操作详情',
    ip_address VARCHAR(50) COMMENT '操作IP',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_time (user_id, create_time),
    INDEX idx_operation (operation)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志表';

-- 初始化管理员账号 (密码: Admin123456, BCrypt)
INSERT INTO sys_user (username, password, real_name, role, status, pwd_reset_required)
VALUES ('admin', '$2b$10$Dputx5F0q3szQJLH1458i.bcUET76edCRA6jWPQFvfYAXq02jQnBq', '系统管理员', 'ADMIN', 'ACTIVE', 0)
ON DUPLICATE KEY UPDATE username=username;

-- AI 出题资源文件表
CREATE TABLE IF NOT EXISTS ai_material (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_name VARCHAR(200) NOT NULL COMMENT '原始文件名',
    file_path VARCHAR(500) NOT NULL COMMENT '存储相对路径',
    file_size BIGINT NOT NULL COMMENT '字节数',
    file_type VARCHAR(20) NOT NULL COMMENT 'pdf/docx/txt/md',
    uploader_id BIGINT NOT NULL COMMENT '上传管理员ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_uploader (uploader_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI出题资源文件表';

-- 题干图片表（应用题富文本插图）
CREATE TABLE IF NOT EXISTS question_image (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_name VARCHAR(200) NOT NULL COMMENT '原始文件名',
    file_path VARCHAR(500) NOT NULL COMMENT '相对路径 data/question-images/xxx.png',
    file_size BIGINT NOT NULL COMMENT '字节数',
    file_type VARCHAR(20) NOT NULL COMMENT 'png/jpg/jpeg/gif/webp',
    uploader_id BIGINT NOT NULL COMMENT '上传管理员ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_uploader (uploader_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题干图片表';

-- 邮箱注册验证码表
CREATE TABLE IF NOT EXISTS verification_code (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel VARCHAR(20) NOT NULL COMMENT 'email/sms',
    target VARCHAR(100) NOT NULL COMMENT '邮箱/手机号',
    code_hash VARCHAR(64) NOT NULL COMMENT 'SHA-256(channel:target:code)',
    expires_at DATETIME NOT NULL COMMENT '过期时间',
    used_at DATETIME COMMENT '使用时间（NULL=未用）',
    last_sent_at DATETIME NOT NULL COMMENT '最近发送时间',
    send_count_today INT NOT NULL DEFAULT 0 COMMENT '当日已发次数',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_target (channel, target, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邮箱注册验证码表';
