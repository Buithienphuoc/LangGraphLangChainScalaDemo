# Bước 1: build fat JAR bằng sbt
FROM sbtscala/scala-sbt:eclipse-temurin-21.0.5_11_1.10.7_3.3.4 AS build
WORKDIR /src
COPY project project
COPY build.sbt .
RUN sbt -batch update
COPY . .
RUN sbt -batch backend/assembly

# Bước 2: image chạy
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/backend/target/scala-3.3.6/healthcare-demo.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
