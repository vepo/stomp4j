FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -pl samples/spring-client-sample -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/samples/spring-client-sample/target/spring-client-sample-*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
