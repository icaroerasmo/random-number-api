FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/random-number-api-0.0.1-SNAPSHOT.jar app.jar

ENV APP_STORAGE_FILE=/app/numbers/latest-number.txt
RUN mkdir -p /app/numbers
VOLUME ["/app/numbers"]

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

