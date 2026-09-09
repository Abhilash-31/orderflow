FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY common-events ./common-events
COPY order-service ./order-service
COPY inventory-service ./inventory-service
COPY payment-service ./payment-service
COPY shipping-service ./shipping-service
RUN mvn -q -pl payment-service -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/payment-service/target/payment-service.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
