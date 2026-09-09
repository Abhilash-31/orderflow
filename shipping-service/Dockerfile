FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY common-events ./common-events
COPY shipping-service ./shipping-service
RUN mvn -q -pl shipping-service -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/shipping-service/target/shipping-service.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
