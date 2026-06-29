FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -pl samples/quarkus-client-sample -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/samples/quarkus-client-sample/target/quarkus-app/ ./
ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
