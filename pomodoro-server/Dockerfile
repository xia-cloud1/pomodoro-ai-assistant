# 番茄钟 · AI 效率分析系统 后端镜像（多阶段构建）
# 构建：docker build -t pomodoro-server .

# ---------- 构建阶段（JDK 25） ----------
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
# 先拷贝 pom 拉取依赖，利用镜像层缓存加速
COPY pom.xml .
RUN mvn dependency:go-offline -B
# 再拷贝源码打包
COPY src ./src
RUN mvn package -DskipTests -B

# ---------- 运行阶段 ----------
FROM eclipse-temurin:25-jre
WORKDIR /app
ENV TZ=Asia/Shanghai
# 中文字体（分享海报 Java2D 渲染）+ 时区数据
RUN apt-get update && apt-get install -y --no-install-recommends \
    fontconfig fonts-noto-cjk tzdata \
    && rm -rf /var/lib/apt/lists/*
# 从构建阶段取 jar
COPY --from=build /app/target/pomodoro-server.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
