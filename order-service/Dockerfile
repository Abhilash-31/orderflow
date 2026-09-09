# Build context is the repo root (see docker-compose.yml) since this is a
# multi-module Maven reactor build — the module needs the parent pom and
# common-events sitting next to it, not just its own directory.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY common-events ./common-events
COPY order-service ./order-service
# -pl builds only this module; -am ("also make") builds its dependencies
# (common-events) first. Avoids pulling in the other three services' code.
RUN mvn -q -pl order-service -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/order-service/target/order-service.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
