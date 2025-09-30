#
# Build stage
#
FROM docker.io/library/maven:3.9-eclipse-temurin-21 AS build
COPY . /home/smart-contract-encapsulator/nebulous-sce-application
WORKDIR /home/smart-contract-encapsulator/nebulous-sce-application
RUN mvn clean package -DskipTests

#
# Package stage
#
FROM docker.io/library/eclipse-temurin:21-jre
WORKDIR /usr/local/lib

COPY --from=build /home/smart-contract-encapsulator/nebulous-sce-application/target/nebulous-sce-application-1.0-SNAPSHOT.jar ./nebulous-sce-application.jar
COPY --from=build /home/smart-contract-encapsulator/nebulous-sce-application/connection-brokerorg.json /usr/local/lib/connection-brokerorg.json

# non-root user
RUN groupadd -r app \
    && useradd -r -g app -d /home/smart-contract-encapsulator/app -s /sbin/nologin -m app \
    && chown -R app:app /usr/local/lib
USER app


ENV ACTIVEMQ_HOST=localhost
ENV ACTIVEMQ_PORT=5672
ENV ACTIVEMQ_USER=admin
ENV ACTIVEMQ_PASSWORD=admin

ENTRYPOINT ["java", "-jar", "nebulous-sce-application.jar"]
CMD ["--activemq-host", "localhost", "--activemq-port", "5672", "--activemq-user", "admin", "--activemq-password", "admin"]