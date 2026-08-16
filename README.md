# 在线作业系统（Online Homework System）

软件设计实践课程项目：面向高校的在线作业管理系统，覆盖作业布置、提交、批改与成绩统计全流程。

## 功能特性

- 三角色：管理员、教师、学生，权限分离
- 作业全生命周期：布置、截止、关闭、作答、批改、成绩导出
- 五种题型：单选、多选、判断、问答、应用题（富文本，支持公式/代码/图表）
- AI 出题：上传课件自动生成题目草稿，文件含题目时自动整理入库
- 教学班双模式：必修（关联自然班级）与选修（树形选人）
- 安全加固：JWT 认证、BCrypt 密码、登录锁定、参数化查询、文件白名单

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 3.2 / MyBatis-Plus / Spring Security / JWT |
| 前端 | Vue 3 / Element Plus（静态资源内嵌，离线可用） |
| 数据库 | MySQL / MariaDB 10.4 |
| 部署 | Docker / Docker Compose（腾讯云） |

## 快速开始

### 本地运行

```bash
# 环境：JDK 17 + MySQL/MariaDB（默认 localhost:3306，库名 homework_system）
# Windows PowerShell
.\mvnw.cmd spring-boot:run
# 或 Linux/macOS
./mvnw spring-boot:run
```

启动后访问 http://localhost:8080 ，接口文档 http://localhost:8080/doc.html 。

测试账号：管理员 admin / Admin123456，教师 T2024001，学生 20240001。

### 运行测试

```bash
.\mvnw.cmd test    # 132 项自动化测试（内存数据库，无需外部依赖）
```

### Docker 部署

```bash
cp .env.example .env   # 配置数据库密码、JWT 密钥、AI/邮件参数
docker compose up -d --build
```

## 项目结构

```
src/main/java/com/xtu/homework
├── controller   # 接口层（admin/teacher/student/auth 分组）
├── service      # 业务层（接口 + 实现）
├── dao          # 数据访问（MyBatis-Plus Mapper）
├── entity       # 实体
├── config       # 安全/跨域/分页等配置
├── util         # JWT、文本提取等工具
└── common       # 统一响应 R、全局异常
src/main/resources/static   # 前端页面（index.html + 本地化第三方库）
```

## 部署环境

- 腾讯云 159.75.31.246，Docker 容器化部署（homework-app + homework-mysql）
- 公网访问：http://159.75.31.246:8080
