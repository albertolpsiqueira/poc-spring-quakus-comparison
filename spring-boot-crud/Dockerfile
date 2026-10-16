FROM eclipse-temurin:25-jre
COPY target/*.jar /app/app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]