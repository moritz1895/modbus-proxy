# syntax=docker/dockerfile:1

# Builder: GraalVM native-image. The image ships the JDK and the gcc toolchain native-image needs
# for its linker step, but no Maven, so Maven is installed on top.
FROM ghcr.io/graalvm/native-image-community:25 AS build

ARG MAVEN_VERSION=3.9.9
RUN curl -fsSL "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
        -o /tmp/maven.tar.gz \
    && tar -xzf /tmp/maven.tar.gz -C /opt \
    && rm /tmp/maven.tar.gz
ENV PATH="/opt/apache-maven-${MAVEN_VERSION}/bin:${PATH}"

WORKDIR /app

# Download dependencies first (better layer caching)
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -Pnative -DskipTests native:compile

# ---

# ghcr.io/graalvm/native-image-community:25 is Oracle Linux (glibc 2.39); ubuntu:24.04 ships the
# same glibc major/minor, so the dynamically linked binary runs unchanged.
FROM ubuntu:24.04
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system proxy && useradd --system --gid proxy --no-create-home proxy
USER proxy

COPY --from=build /app/target/modbus-proxy ./modbus-proxy

# 502: Modbus proxy port  8080: Spring Boot Actuator
EXPOSE 502 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

# A native image has a fixed serial GC and no JIT; -Xmx caps the heap at the same 128 MB the JVM
# deployment used. MODBUS_PROXY_NATIVE_OPTS replaces it when set.
ENV MODBUS_PROXY_NATIVE_OPTS="-Xmx128m"

ENTRYPOINT ["sh", "-c", "exec ./modbus-proxy $MODBUS_PROXY_NATIVE_OPTS"]
