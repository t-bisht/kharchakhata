# login_engine — multi-stage Spring Boot image.
#
# Prereq: build the fat JAR first from the /java root:
#   ./gradlew :login_engine:bootJar --no-daemon
# Output: login_engine/build/libs/login_engine-0.1.0-SNAPSHOT.jar
#
# Build context: java/login_engine/ (set in docker-compose.yml).

FROM eclipse-temurin:21-jre-alpine AS layertools
WORKDIR /extract
COPY build/libs/login_engine-0.1.0-SNAPSHOT.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup --system appgroup \
 && adduser  --system --ingroup appgroup --no-create-home appuser

COPY --from=layertools /extract/dependencies/          ./
COPY --from=layertools /extract/spring-boot-loader/    ./
COPY --from=layertools /extract/snapshot-dependencies/ ./
COPY --from=layertools /extract/application/           ./

ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
USER appuser

EXPOSE 8082

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8082/api/actuator/health || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
