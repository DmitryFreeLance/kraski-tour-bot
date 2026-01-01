FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app

# БД по умолчанию в volume /data
ENV DB_PATH=/data/bot.db

VOLUME ["/data"]

COPY --from=build /app/target/kraski-tour-bot.jar /app/bot.jar

ENTRYPOINT ["java","-jar","/app/bot.jar"]