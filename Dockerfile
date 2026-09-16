FROM eclipse-temurin:25-jre-alpine

# Корневой сертификат Минцифры: им подписан сертификат invest-public-api.tinkoff.ru
COPY certs/russian_trusted_root_ca.pem /tmp/russian_trusted_root_ca.pem
RUN keytool -importcert -noprompt -cacerts -storepass changeit \
      -alias russian_trusted_root_ca -file /tmp/russian_trusted_root_ca.pem \
    && rm /tmp/russian_trusted_root_ca.pem

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
USER appuser

# JAR кладётся CD-пайплайном перед docker build
COPY app/target/app-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
