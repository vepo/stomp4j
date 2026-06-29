FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -pl bridge/stomp4j-kafka-bridge-runner -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/bridge/stomp4j-kafka-bridge-runner/target/stomp4j-kafka-bridge-runner-*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
