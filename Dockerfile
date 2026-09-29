FROM eclipse-temurin:25-jre-alpine

# Корневой сертификат Минцифры: им подписан сертификат invest-public-api.tinkoff.ru
COPY certs/russian_trusted_root_ca.pem /tmp/russian_trusted_root_ca.pem
RUN keytool -importcert -noprompt -cacerts -storepass changeit \
      -alias russian_trusted_root_ca -file /tmp/russian_trusted_root_ca.pem \
    && rm /tmp/russian_trusted_root_ca.pem

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
USER appuser

# Раскладку собирает `mvn package` в app/target/dockerbuild (см. maven-dependency-plugin и maven-antrun-plugin в pom.xml):
#   ext - сторонние библиотеки: меняются редко, поэтому идут отдельным слоем раньше и берутся из кэша
#   lib - модули проекта и сам app: меняются почти в каждой сборке
COPY app/target/dockerbuild/ext/ ext/
COPY app/target/dockerbuild/lib/ lib/

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75.0", \
  "-cp", "/app/lib/*:/app/ext/*", \
  "ru.invest.api.InvestApplication"]
