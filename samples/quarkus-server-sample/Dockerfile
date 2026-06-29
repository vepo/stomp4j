FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -pl samples/quarkus-server-sample -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/samples/quarkus-server-sample/target/quarkus-app/ ./
ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
