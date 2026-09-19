# syntax=docker/dockerfile:1

# Сборка идёт внутри образа: на сервере не нужны ни JDK, ни maven,
# ни заранее собранный target/MySite.jar — хватает docker compose up --build.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# На сервере с 1 ГБ памяти maven по умолчанию берёт четверть от неё и
# может не дотянуть на компиляции. Явный потолок надёжнее автоматики.
ENV MAVEN_OPTS="-Xmx512m"

COPY pom.xml .
COPY src ./src

# Кэш ~/.m2 переживает пересборки, поэтому зависимости качаются один раз.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q clean package -DskipTests

FROM amazoncorretto:21
WORKDIR /app

COPY --from=build /build/target/MySite.jar app.jar

# Наружу порт не публикуется: до приложения ходит только Caddy по внутренней сети.
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
