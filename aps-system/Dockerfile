# 第一阶段：Maven 构建
FROM maven:3.9.6-eclipse-temurin-11 AS builder
WORKDIR /build
COPY pom.xml .
# 先下载依赖（利用缓存）
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn clean package -DskipTests -q

# 第二阶段：运行
FROM eclipse-temurin:11-jre
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
