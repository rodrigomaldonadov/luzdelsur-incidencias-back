# syntax=docker/dockerfile:1
# Imagen del backend. Construir desde la raiz del repositorio:
#   docker build -t luzdelsur/incidencias-backend .

FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace/backend

COPY backend/pom.xml .
RUN mvn -B -q dependency:go-offline

COPY backend/src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build /workspace/backend/target/app.jar /app/app.jar

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport -XX:+ExitOnOutOfMemoryError" \
    SERVER_PORT=8080

USER app
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
