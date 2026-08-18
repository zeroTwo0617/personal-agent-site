# ============================================================
# 个人 AI 分身站 · 后端镜像（多阶段构建，在 CI 上构建，服务器只 pull）
#   阶段1 build：Maven + Temurin 21 编译
#   阶段2 run  ：Temurin 21 JRE，只复制 fat jar，镜像体积最小化
# ============================================================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# 先拷贝 pom 并预拉依赖：pom 不变时该层可复用 Docker 缓存，加速重复构建
COPY pom.xml .
RUN mvn -B dependency:go-offline || true

COPY src ./src
RUN mvn -B -DskipTests package

# ---------- 运行阶段 ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

# JAVA_OPTS 由 compose 注入（默认 -Xmx768m，2C2G 内存机预算内）
# 密钥经 compose/运行时 -e 环境变量注入（见 deploy/.env.example）
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
