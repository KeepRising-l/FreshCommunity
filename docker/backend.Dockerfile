# 后端镜像：多阶段构建（Maven 构建 → JRE 运行）
# 构建：docker build -f docker/backend.Dockerfile -t freshcommunity-backend .
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY backend/pom.xml .
# 先拉依赖：pom 不变时命中缓存层
RUN mvn -q dependency:go-offline
COPY backend/src ./src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
# uploads 卷挂载点（用户上传的头像/提货点照片/商品图）
RUN mkdir -p /app/uploads
VOLUME /app/uploads
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
