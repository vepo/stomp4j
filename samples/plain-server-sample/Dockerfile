FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -pl samples/plain-server-sample -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/samples/plain-server-sample/target/plain-server-sample-*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
