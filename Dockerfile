### ---------------------------------------------------------------------------
### NewTabLinks backend
###
### Multi-stage: a JDK image compiles the application, a JRE image runs it, so
### the shipped image carries no compiler, no Maven and no sources.
###
### Built by .github/workflows/docker-build.yml, which delegates to the shared
### pipeline in Kovospace/kovostack-github-workflows. The build context is the
### repository root.
### ---------------------------------------------------------------------------


### ---------- build stage ----------
#
# Java 25 is the current LTS and the version the application targets. Pinned
# explicitly rather than tracking `latest`, so a rebuild of an old commit still
# compiles with the toolchain that commit was written for.
#
FROM eclipse-temurin:25-jdk AS build

WORKDIR /src


### Maven wrapper and build descriptor first. Docker caches this layer, so
### dependencies are re-downloaded only when pom.xml actually changes - not on
### every source edit.
#
COPY .mvn/ ./.mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline


### Application sources.
#
COPY src ./src


### Build the executable jar. Tests are skipped here on purpose: the image build
### is the packaging step, and the pipeline is where tests belong - a test that
### needs a database cannot run inside a docker build anyway.
#
RUN ./mvnw -B -ntp clean package -DskipTests \
 && cp target/*.jar /application.jar


### ---------- runtime stage ----------
#
FROM eclipse-temurin:25-jre

WORKDIR /app


### Run as an unprivileged user. The JRE image ships no non-root user of its
### own, so one is created here.
#
RUN groupadd --system --gid 1001 application \
 && useradd --system --uid 1001 --gid application application


COPY --from=build --chown=application:application /application.jar ./application.jar

USER application


### Documentation only - publishing the port is the deployment's job. Matches
### server.port in application.properties and containerPort in the Helm chart.
#
EXPOSE 8080


### Container-aware defaults: let the JVM size the heap from the cgroup limit
### the deployment sets, instead of guessing from the host's total memory.
#
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"


### Started through sh so JAVA_OPTS is expanded; `exec` hands PID 1 to the JVM
### so it receives SIGTERM directly on pod shutdown.
#
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/application.jar"]
