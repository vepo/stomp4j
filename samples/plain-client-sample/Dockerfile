FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -pl samples/plain-client-sample -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/samples/plain-client-sample/target/plain-client-sample-*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
