FROM public.ecr.aws/amazoncorretto/amazoncorretto:17
WORKDIR /app
COPY target/challenge-platform-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
