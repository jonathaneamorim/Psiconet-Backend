# Stage 1: build + testes (H2 em memória; nunca toca em banco real). Falha de teste impede a imagem.
FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
# Documentos legais (Termos, Privacidade, Cookies) empacotados no jar
COPY docs/legal ./docs/legal
RUN mvn -B clean package

# Stage 2: runtime só com JRE, usuário sem privilégios
FROM eclipse-temurin:17-jre
ENV TZ=America/Sao_Paulo
RUN groupadd --system psiconet && useradd --system --gid psiconet --home-dir /app --shell /usr/sbin/nologin psiconet \
    && mkdir -p /app/uploads && chown -R psiconet:psiconet /app
WORKDIR /app
COPY --from=builder --chown=psiconet:psiconet /build/target/*.jar app.jar
USER psiconet
EXPOSE 8080
# Endpoint público e barato (só lê os documentos legais em memória)
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /legal/versions HTTP/1.0\r\nHost: localhost\r\n\r\n" >&3 && head -1 <&3 | grep -q " 200 "'
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0","-Duser.timezone=America/Sao_Paulo","-jar","/app/app.jar"]
