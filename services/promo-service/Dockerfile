FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/promo-service-1.0.0.jar app.jar
EXPOSE 8087
ENTRYPOINT ["java","-jar","app.jar"]
