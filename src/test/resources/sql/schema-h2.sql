-- =====================================================
-- H2 数据库 Schema（MySQL 兼容模式）
-- 注意：user 是 H2 保留关键字，需用双引号包裹
-- =====================================================

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(200) NOT NULL,
    real_name VARCHAR(50) NOT NULL,
    role VARCHAR(20) NOT NULL,
    phone VARCHAR(20),
    email VARCHAR(100),
    clazz_id BIGINT,
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    pwd_reset_required BOOLEAN NOT NULL DEFAULT TRUE,
    login_fail_count INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS clazz (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    grade VARCHAR(20) NOT NULL,
    major VARCHAR(100) NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS teaching_class (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    teacher_id BIGINT NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS teaching_class_clazz (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    teaching_class_id BIGINT NOT NULL,
    clazz_id BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS knowledge_point (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    subject VARCHAR(100),
    parent_id BIGINT,
    description VARCHAR(500),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS question (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(20) NOT NULL,
    content CLOB NOT NULL,
    correct_answer VARCHAR(500),
    reference_answer CLOB,
    score DECIMAL(5,2) NOT NULL DEFAULT 5.00,
    difficulty VARCHAR(10) NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    tfidf_vector CLOB,
    creator_id BIGINT NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS question_option (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    question_id BIGINT NOT NULL,
    label VARCHAR(5) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    sort_order INT NOT NULL
);

CREATE TABLE IF NOT EXISTS question_knowledge (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    question_id BIGINT NOT NULL,
    knowledge_point_id BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS homework (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description CLOB,
    teaching_class_id BIGINT NOT NULL,
    teacher_id BIGINT NOT NULL,
    deadline TIMESTAMP NOT NULL,
    total_score DECIMAL(6,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    question_locked BOOLEAN NOT NULL DEFAULT FALSE,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS homework_question (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    homework_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    sort_order INT NOT NULL,
    score DECIMAL(5,2) NOT NULL
);

CREATE TABLE IF NOT EXISTS submission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    homework_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NOT_SUBMITTED',
    open_time TIMESTAMP,
    submit_time TIMESTAMP,
    last_modified_time TIMESTAMP,
    duration_seconds INT,
    auto_score DECIMAL(6,2),
    manual_score DECIMAL(6,2),
    total_score DECIMAL(6,2),
    suspicious_flag BOOLEAN DEFAULT FALSE,
    CONSTRAINT uk_submission UNIQUE (homework_id, student_id)
);

CREATE TABLE IF NOT EXISTS submission_answer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    submission_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    student_answer CLOB,
    score DECIMAL(5,2),
    comment VARCHAR(500),
    graded_by BIGINT,
    graded_time TIMESTAMP
);

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    username VARCHAR(50) NOT NULL,
    operation VARCHAR(50) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id VARCHAR(100),
    detail CLOB,
    ip_address VARCHAR(50),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- AI 出题资源文件表
CREATE TABLE IF NOT EXISTS ai_material (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_name VARCHAR(200) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    file_type VARCHAR(20) NOT NULL,
    uploader_id BIGINT NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 邮箱注册验证码表
CREATE TABLE IF NOT EXISTS verification_code (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel VARCHAR(20) NOT NULL,
    target VARCHAR(100) NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    last_sent_at TIMESTAMP NOT NULL,
    send_count_today INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
