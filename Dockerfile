# syntax=docker/dockerfile:1
# Una sola receta para los cinco servicios: el módulo se elige con el argumento MODULE.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
ARG MODULE
# La caché de ~/.m2 se comparte entre las cinco imágenes: las dependencias se bajan una sola vez.
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -q -pl ${MODULE} -am package -DskipTests

FROM eclipse-temurin:21-jre
ARG MODULE
WORKDIR /app
COPY --from=build /src/${MODULE}/target/${MODULE}-*.jar app.jar
RUN useradd --system --uid 1001 app
USER app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
