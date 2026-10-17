FROM container-registry.oracle.com/graalvm/jdk:25
COPY target/*.jar /app/app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]