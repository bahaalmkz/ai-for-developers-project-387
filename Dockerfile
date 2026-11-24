# Образ всего приложения: Spring Boot отдаёт API (/api) и собранный фронтенд на одном порту.
# Порт берётся из переменной окружения PORT (по умолчанию 8080).
#
#   docker build -t calendar .
#   docker run --rm -e PORT=8080 -p 8080:8080 calendar

# 1. Фронтенд: клиентский SDK из contract/openapi.yaml и сборка Vite.
#    openapi-generator-cli запускает Java, поэтому в образ Node добавляется JRE.
FROM node:26.13.1-slim AS frontend
COPY --from=eclipse-temurin:21-jre /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
WORKDIR /src
COPY frontend/package.json frontend/package-lock.json frontend/openapitools.json frontend/
RUN cd frontend && npm ci
COPY contract/openapi.yaml contract/
COPY frontend/ frontend/
RUN cd frontend && npm run generate:api && npm run build

# 2. Бэкенд: серверные интерфейсы генерируются из того же контракта при сборке Maven,
#    собранный фронтенд кладётся в classpath:/static/.
FROM eclipse-temurin:21-jdk AS backend
# Maven Wrapper скачивает Maven через curl и распаковывает unzip
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl unzip \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /src
COPY contract/openapi.yaml contract/
COPY backend/.mvn backend/.mvn
COPY backend/mvnw backend/pom.xml backend/
COPY backend/src backend/src
COPY --from=frontend /src/frontend/dist backend/src/main/resources/static
# Тесты здесь не запускаются: их прогоняет mise run check в CI до сборки образа
RUN cd backend \
    && chmod +x mvnw \
    && ./mvnw -B -q package -DskipTests \
    && cp target/calendar-*.jar /src/app.jar

# 3. Запуск: только JRE и jar.
FROM eclipse-temurin:21-jre
WORKDIR /app
# Файл H2 пишется в /app/data. В контейнере это не постоянное хранилище: см. README.
RUN useradd --system --uid 10001 --home-dir /app app \
    && mkdir data \
    && chown app:app data
COPY --from=backend /src/app.jar app.jar
USER app
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
