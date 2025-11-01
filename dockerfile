FROM eclipse-temurin:22-jdk
WORKDIR /app

COPY . /app
COPY ./config/systems.properties /app/systems.properties

# Install missing Hive dependency
RUN ./mvnw install:install-file \
  -Dfile=lib/hive-jdbc-uber-2.6.3.0-235.jar \
  -DgroupId=veil.hdp.hive \
  -DartifactId=hive-jdbc-uber \
  -Dversion=2.6.3.0-235 \
  -Dpackaging=jar

RUN chmod +x mvnw
RUN ./mvnw clean package -DskipTests

COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
