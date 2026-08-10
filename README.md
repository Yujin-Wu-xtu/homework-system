# 在线作业系统 - 后端项目源代码

## 项目简介
本项目是《软件设计实践》课程中在线作业系统的后端实现，基于 Spring Boot 3 + MyBatis-Plus + Spring Security + JWT 技术栈。

## 技术栈
- Spring Boot 3.x
- MyBatis-Plus 3.5
- Spring Security 6.x + JWT
- MySQL 8.0
- Druid 连接池
- Knife4j 接口文档
- Apache POI (Excel 导入导出)

## 项目结构
```
src/main/java/com/xtu/homework/
├── HomeworkApplication.java          # 启动类
├── common/
│   └── R.java                        # 统一响应结果
├── config/
│   ├── SecurityConfig.java           # Spring Security 安全配置
│   ├── JwtAuthenticationFilter.java  # JWT 认证过滤器
│   └── CorsConfig.java               # 跨域配置
├── controller/
│   ├── AuthController.java           # 认证接口
│   ├── AdminController.java          # 管理员接口
│   ├── TeacherController.java        # 教师接口
│   └── StudentController.java        # 学生接口
├── service/
│   ├── UserService.java              # 用户服务接口
│   ├── QuestionService.java          # 题目服务接口
│   ├── HomeworkService.java          # 作业服务接口
│   ├── SubmissionService.java        # 提交服务接口
│   └── impl/
│       ├── UserServiceImpl.java
│       ├── QuestionServiceImpl.java
│       ├── HomeworkServiceImpl.java
│       └── SubmissionServiceImpl.java
├── dao/
│   ├── UserDao.java
│   ├── ClazzDao.java
│   ├── TeachingClassDao.java
│   ├── QuestionDao.java
│   ├── KnowledgePointDao.java
│   ├── HomeworkDao.java
│   ├── HomeworkQuestionDao.java
│   ├── SubmissionDao.java
│   ├── SubmissionAnswerDao.java
│   └── AuditLogDao.java
├── entity/
│   ├── User.java
│   ├── Clazz.java
│   ├── TeachingClass.java
│   ├── TeachingClassClazz.java
│   ├── Question.java
│   ├── QuestionOption.java
│   ├── QuestionKnowledge.java
│   ├── KnowledgePoint.java
│   ├── Homework.java
│   ├── HomeworkQuestion.java
│   ├── Submission.java
│   ├── SubmissionAnswer.java
│   └── AuditLog.java
├── dto/
│   ├── LoginDto.java
│   ├── HomeworkAssignDto.java
│   ├── SubmissionDto.java
│   └── GradingDto.java
└── util/
    └── JwtUtil.java                  # JWT 工具类
```

## 本地运行

```powershell
# Windows（首选，需要本机 XAMPP MySQL 已启动）
.\mvnw.cmd spring-boot:run
```

- 默认账号：admin / T2024001 / 20240001~20240006，密码 `Admin123456`（首次登录强制改密）
- 接口文档：http://localhost:8080/doc.html
- 数据库：MySQL（本地 XAMPP MariaDB，库 `homework_system`）；生产用环境变量 `DB_HOST/DB_PORT/DB_PASSWORD` 注入（见 DEPLOY.md）

## 服务器部署

见 [DEPLOY.md](DEPLOY.md)（Docker Compose：应用 + MariaDB + 数据卷持久化，一键 `docker compose up -d --build`）。
