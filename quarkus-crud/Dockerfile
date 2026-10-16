FROM eclipse-temurin:25-jre
COPY target/quarkus-app/ /app/
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]