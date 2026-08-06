# =====================================================
# 在线作业系统 Docker 多阶段构建
# Stage 1: 使用 Maven 编译打包
# Stage 2: 使用 JRE 运行
# =====================================================

# ====== Stage 1: 编译 ======
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn package -DskipTests -q

# ====== Stage 2: 运行 ======
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 安装健康检查工具
RUN apk add --no-cache wget

# 创建非 root 用户
RUN addgroup -g 1000 appgroup && \
    adduser -u 1000 -G appgroup -D appuser

# 复制 JAR
COPY --from=builder /app/target/*.jar app.jar

# 创建数据目录
RUN mkdir -p /app/data && chown -R appuser:appgroup /app

USER appuser
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8080/ || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
