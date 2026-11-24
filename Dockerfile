# syntax=docker/dockerfile:1
# SEMA ALERTA API — imagem multi-stage (Issue #2). AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS.
# Nenhum segredo nesta imagem: toda configuração sensível chega por variáveis de ambiente
# em tempo de execução (docker-compose.yml + .env, Issue #8).

# ---------- Stage 1: build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Cache de dependências: só invalida quando o pom.xml muda.
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -DskipTests package \
    && cp target/sema-nexus-api.jar /workspace/app.jar

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:21-jre-alpine AS runtime

ARG APP_UID=10001
ARG APP_GID=10001

# OCR opcional (Issue #140): Tesseract (Apache 2.0) + dados do idioma português. Sem o binário a API degrada com aviso.
RUN apk add --no-cache tesseract-ocr tesseract-ocr-data-por \
    && addgroup -S -g ${APP_GID} semanexus \
    && adduser -S -D -H -u ${APP_UID} -G semanexus -s /sbin/nologin semanexus \
    && mkdir -p /app /data/uploads \
    && chown -R ${APP_UID}:${APP_GID} /app /data

WORKDIR /app
COPY --from=build --chown=${APP_UID}:${APP_GID} /workspace/app.jar /app/app.jar

# Valores não sensíveis; podem ser sobrescritos pelo compose/.env.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError" \
    STORAGE_DIR=/data/uploads \
    TESSERACT_CMD=tesseract \
    TESSERACT_LANG=por \
    SERVER_PORT=8080

USER ${APP_UID}:${APP_GID}

VOLUME ["/data/uploads"]
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -qO- "http://localhost:${SERVER_PORT}/actuator/health" > /dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar \"$@\"", "--"]
