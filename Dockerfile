FROM amazoncorretto:21

WORKDIR /app

COPY target/MySite.jar app.jar

CMD ["java", "-jar", "app.jar"]