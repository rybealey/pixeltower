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

# ---- Stage 2: Slim runtime -------------------------------------------------
FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache bash gettext

WORKDIR /emulator

# Baked-in artifacts. Host bind-mount at /emulator can override these.
COPY --from=builder /build/Emulator.jar /opt/arcturus/Emulator.jar
COPY --from=builder /build/sqlupdates   /opt/arcturus/sqlupdates

COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

EXPOSE 3000 3001 2095

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
