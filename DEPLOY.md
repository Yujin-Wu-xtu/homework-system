# 在线作业系统 — 服务器部署指南（Docker）

> 适用于课程要求的"远程服务器 Docker 部署验证"。本地开发用 `.\mvnw.cmd spring-boot:run`（见 README），本指南针对生产/服务器环境。

## 1. 前置条件

- 服务器：Linux（Ubuntu/Debian/CentOS 均可），已安装 Docker + Docker Compose v2
  ```bash
  docker --version && docker compose version
  ```
- 开放端口：`8080`（应用，公网或校园网可达）；MySQL 不出现在宿主机端口（仅容器内网，安全）
- 磁盘：镜像约 1GB，数据卷另计

## 2. 部署步骤

```bash
# 1) 拷贝项目到服务器（或用 git）
# 2) 准备环境变量
cp .env.example .env
vi .env        # 必须修改：DB_PASSWORD（数据库密码）、JWT_SECRET（JWT 密钥）
               # 可选：DEEPSEEK_API_KEY（AI 出题）、MAIL_AUTH_CODE（邮箱注册）

# 3) 构建并启动（首次构建约 3-5 分钟）
docker compose up -d --build

# 4) 查看状态
docker compose ps
docker compose logs -f app      # 看到 "Started HomeworkApplication" 即启动完成
```

## 3. 验证

| 检查项 | 方法 | 预期 |
|---|---|---|
| 应用健康 | `curl -sI http://localhost:8080/` | HTTP 200 |
| 登录页 | 浏览器访问 `http://<服务器IP>:8080` | 登录页正常 |
| 接口文档 | `http://<服务器IP>:8080/doc.html` | Knife4j 文档页 |
| 默认账号 | admin / T2024001 / 20240001~20240006，密码 `Admin123456` | 首次登录强制改密 |
| 数据库初始化 | 首次启动自动建表+灌初始数据（幂等，重启不重复） | 无异常日志 |

> 若 `doc.html` 打开空白：稍等几秒或强刷（Knife4j 首次加载资源较慢）。

## 4. 环境变量说明（.env）

| 变量 | 必填 | 说明 |
|---|---|---|
| `DB_PASSWORD` | ✅ | MySQL root 密码。**首次启动后改它需重建 mysql 卷**（数据会清空，见第 6 节） |
| `JWT_SECRET` | ✅ | JWT 签名密钥，≥32 字符。更换后所有已登录会话失效（重新登录即可） |
| `DEEPSEEK_API_KEY` | 可选 | AI 出题密钥；不配置则题库"AI 生成"提示"AI 服务未配置" |
| `MAIL_AUTH_CODE` | 可选 | QQ 邮箱授权码；不配置则注册"获取验证码"发信失败（邮箱注册是非主路径） |

## 5. 数据持久化与备份

两个 Docker 卷，删除容器不丢数据：

- `homework_mysql-data`：MySQL 库表（homework_system）
- `homework_app-data`：应用数据（`data/ai-materials/` AI 课件、`data/question-images/` 题干图片）

备份（服务器上定时或手动）：

```bash
# MySQL 全量导出
docker compose exec mysql sh -c 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysqldump -u root homework_system' > homework_backup_$(date +%F).sql

# 应用数据
docker run --rm -v homework_app-data:/data -v $(pwd):/backup alpine tar czf /backup/appdata_$(date +%F).tar.gz -C /data .
```

## 6. 常见问题

| 症状 | 原因 / 解决 |
|---|---|
| 应用反复重启（`depends_on` 等待） | MySQL 首次初始化较慢，`docker compose ps` 等 mysql 变 healthy 后 app 自动启动 |
| 改 DB_PASSWORD 后应用连不上 | 密码只在 mysql 卷首次初始化时生效；改密码 = `docker compose down -v` 清卷重来（**数据会清空**） |
| AI 出题报"AI 服务未配置" | .env 未设 DEEPSEEK_API_KEY，或改 .env 后需 `docker compose up -d`（compose 会重建应用容器） |
| 图片/课件上传后重启丢失 | app-data 卷被误删；正常重启不丢 |
| 生产 SQL 日志 | 已默认静默（MYBATIS_LOG_IMPL=Slf4jImpl）；开发想看 SQL 在本地跑，别在生产开 StdOutImpl |

## 7. 停止 / 更新

```bash
docker compose down          # 停止（数据卷保留）
docker compose down -v       # 停止并清空数据（慎用！）
git pull && docker compose up -d --build   # 更新版本
```
