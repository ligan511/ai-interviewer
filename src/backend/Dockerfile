# 多阶段构建：先用 Maven 编译，再用精简 JRE 运行
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
# 先单独拷贝 pom 利用缓存加速依赖下载
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
