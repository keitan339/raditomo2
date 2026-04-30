FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
RUN apk add --no-cache maven
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline -DskipTests || true
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache ffmpeg tzdata && \
    cp /usr/share/zoneinfo/Asia/Tokyo /etc/localtime && \
    echo "Asia/Tokyo" > /etc/timezone
ENV TZ=Asia/Tokyo

WORKDIR /app
COPY --from=builder /build/target/raditomo-*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
