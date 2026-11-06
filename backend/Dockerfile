# syntax=docker/dockerfile:1
FROM eclipse-temurin:21.0.13_9-jdk-noble AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21.0.13_9-jre-noble
RUN groupadd --system app && useradd --system --gid app app
WORKDIR /app
COPY --from=build /workspace/target/tradelens-*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
