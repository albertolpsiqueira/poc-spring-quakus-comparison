FROM container-registry.oracle.com/graalvm/jdk:25
COPY target/quarkus-app/ /app/
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]