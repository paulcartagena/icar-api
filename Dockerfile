# Build on the runner's native platform: the jar is platform-independent, so only the runtime stage
# below is per-architecture (lets CI build an ARM image too without emulating the whole Maven build)
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn ./.mvn
# Dependencies in their own layer: only downloaded again when pom.xml changes
RUN ./mvnw -B -q dependency:go-offline
COPY src ./src
# Tests run in CI before the image is built
RUN ./mvnw -B -q package -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /app/target/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
