FROM container-registry.oracle.com/graalvm/jdk:25
COPY target/quarkus-app/ /app/
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]