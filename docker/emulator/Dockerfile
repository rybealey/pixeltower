# syntax=docker/dockerfile:1.7

# ── Stage 1: build Arcturus Morningstar emulator ────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS emu-build
WORKDIR /src
RUN git clone --depth 1 --branch dev \
      https://git.krews.org/morningstar/Arcturus-Community.git arcturus
WORKDIR /src/arcturus
RUN mvn -B -DskipTests package
# Arcturus packages a fat jar under target/; normalize its name
RUN set -eux; \
    cd target; \
    FAT=$(ls Habbo-*-jar-with-dependencies.jar 2>/dev/null | head -n1 || true); \
    if [ -z "$FAT" ]; then FAT=$(ls *-jar-with-dependencies.jar | head -n1); fi; \
    cp "$FAT" /emulator.jar

# ── Stage 2: build ms-websockets plugin ─────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS plugin-build
WORKDIR /src
RUN git clone --depth 1 \
      https://git.krews.org/nitro/ms-websockets.git ms-websockets
WORKDIR /src/ms-websockets
# Arcturus plugins depend on the emulator as a compile-time artifact.
# Install the fat jar into the local Maven repo so the plugin's pom resolves.
COPY --from=emu-build /emulator.jar /tmp/emulator.jar
RUN mvn install:install-file \
      -Dfile=/tmp/emulator.jar \
      -DgroupId=com.eu.habbo \
      -DartifactId=Habbo \
      -Dversion=3.5.4-SNAPSHOT \
      -Dpackaging=jar || true
RUN mvn -B -DskipTests package
RUN set -eux; \
    cd target; \
    PJAR=$(ls *.jar | grep -vE 'original|sources|javadoc' | head -n1); \
    cp "$PJAR" /ms-websockets.jar

# ── Stage 3: runtime ────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime
RUN apk add --no-cache bash gettext tzdata

WORKDIR /emulator
COPY --from=emu-build    /emulator.jar       /emulator/emulator.jar
COPY --from=plugin-build /ms-websockets.jar  /emulator/plugins-baked/ms-websockets.jar
COPY entrypoint.sh /entrypoint.sh
COPY --chmod=0644 config.ini.template /emulator/config.ini.template
RUN chmod +x /entrypoint.sh \
  && mkdir -p /emulator/plugins /emulator/logging

EXPOSE 3000 3001 2095

ENTRYPOINT ["/entrypoint.sh"]
