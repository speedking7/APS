FROM eclipse-temurin:11-jre
WORKDIR /app
COPY target/aps-system-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
