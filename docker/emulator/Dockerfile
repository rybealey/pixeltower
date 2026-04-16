# ---- Stage 1: Build the fat JAR from source --------------------------------
# Arcturus Morningstar Community targets Java 1.8 source/target, but we use
# JDK 17 + Maven to build; bytecode is still 1.8-compatible.
FROM maven:3.9-eclipse-temurin-17 AS builder

ARG ARCTURUS_REPO=https://git.krews.org/morningstar/Arcturus-Community.git
ARG ARCTURUS_REF=master

WORKDIR /build
RUN git clone --depth 1 --branch "${ARCTURUS_REF}" "${ARCTURUS_REPO}" . \
 && mvn -B -DskipTests package \
 && mv target/Habbo-*-jar-with-dependencies.jar /build/Emulator.jar \
 && mkdir -p /build/sqlupdates && cp -r sqlupdates/. /build/sqlupdates/ 2>/dev/null || true

# ---- Stage 1b: Fetch plugin JARs -------------------------------------------
# NitroWebsockets adds WS/Nitro support to Arcturus MS 3.0.0+. The plugin's
# pom depends on a Maven artifact that isn't published, so we grab the
# prebuilt JAR the author commits to target/.
FROM alpine:3.20 AS plugins
ARG NITROWS_REPO=https://git.krews.org/morningstar/nitrowebsockets-for-ms.git
ARG NITROWS_REF=master
RUN apk add --no-cache git
WORKDIR /plugins
RUN git clone --depth 1 --branch "${NITROWS_REF}" "${NITROWS_REPO}" src \
 && cp src/target/NitroWebsockets-*.jar /plugins/ \
 && rm -rf src

# ---- Stage 2: Slim runtime -------------------------------------------------
# Using the Ubuntu-based JRE (not -alpine) because alpine doesn't publish an
# arm64 variant and we build on Apple Silicon as well as x86_64 VPSes.
FROM eclipse-temurin:17-jre

RUN apt-get update && apt-get install -y --no-install-recommends gettext-base bash \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /emulator

# Baked-in artifacts. Host bind-mount at /emulator can override these.
COPY --from=builder /build/Emulator.jar /opt/arcturus/Emulator.jar
COPY --from=builder /build/sqlupdates   /opt/arcturus/sqlupdates
COPY --from=plugins /plugins/           /opt/arcturus/default-plugins/

COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

EXPOSE 3000 3001 2095

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
