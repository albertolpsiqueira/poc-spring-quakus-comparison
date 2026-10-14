FROM eclipse-temurin:25-jre
COPY target/quarkus-app/ /app/
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]