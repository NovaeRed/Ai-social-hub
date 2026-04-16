FROM bellsoft/liberica-openjdk-alpine:21.0.8-cds

LABEL description="Ai-social-hub Spring Boot application" \
      maintainer="NovaeRed" \
      version="1.0.0"

ENV APP_DIR=/app
ENV JAVA_OPTS=""

WORKDIR ${APP_DIR}

COPY ./target/Ai-social-hub.jar ${APP_DIR}/app.jar

RUN mkdir -p ${APP_DIR}/log
VOLUME ${APP_DIR}/log

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]