# Один Dockerfile на все Java-сервисы: docker build --build-arg SERVICE=intake -f Dockerfile .
# Сборка внутри образа — стенд поднимается с чистого клона одной командой (ADR-014).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts ./
COPY gradle gradle
COPY build-logic build-logic
COPY config config
COPY libs libs
ARG SERVICE
COPY ${SERVICE} ${SERVICE}
RUN --mount=type=cache,target=/root/.gradle,sharing=locked ./gradlew --no-daemon -q :${SERVICE}:bootJar -x test -x checkstyleMain \
 && java -Djarmode=tools -jar ${SERVICE}/build/libs/${SERVICE}.jar extract --layers --launcher --destination /out

FROM eclipse-temurin:21-jre
RUN useradd -r -u 10001 app
WORKDIR /app
COPY --from=build /out/dependencies/ ./
COPY --from=build /out/spring-boot-loader/ ./
COPY --from=build /out/snapshot-dependencies/ ./
COPY --from=build /out/application/ ./
USER 10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
