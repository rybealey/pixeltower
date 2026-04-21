# syntax=docker/dockerfile:1.7
#
# Build context is the repo root (see docker-compose.yml emulator.build.context).
# That lets us COPY plugins/pixeltower-rp/ directly; all docker/emulator/* paths
# are prefixed accordingly.

# ── Stage 1: build Arcturus Morningstar emulator ────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS emu-build
WORKDIR /src
RUN git clone --depth 1 --branch dev \
      https://git.krews.org/morningstar/Arcturus-Community.git arcturus
# Apply local Arcturus patches (if any). Mirror of the nitro-patches flow in
# scripts/build-client.sh. Each *.patch under arcturus-patches/ in the repo
# root is a standard `git apply` diff against the upstream dev branch.
COPY arcturus-patches /patches
WORKDIR /src/arcturus
RUN set -eux; \
    shopt -s nullglob 2>/dev/null || true; \
    for p in /patches/*.patch; do \
      [ -f "$p" ] || continue; \
      echo "applying $p"; \
      git apply --whitespace=nowarn "$p"; \
    done
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
# Register the Arcturus fat jar under the version the plugin's pom requires.
RUN mvn install:install-file \
      -Dfile=/tmp/emulator.jar \
      -DgroupId=com.eu.habbo \
      -DartifactId=Habbo \
      -Dversion=3.0.0 \
      -Dpackaging=jar
RUN mvn -B -DskipTests package
RUN set -eux; \
    cd target; \
    PJAR=$(ls *.jar | grep -vE 'original|sources|javadoc' | head -n1); \
    cp "$PJAR" /ms-websockets.jar

# ── Stage 3: build pixeltower-rp plugin (local source) ──────────────────────
FROM maven:3.9-eclipse-temurin-17 AS pixeltower-plugin-build
WORKDIR /src
# Install the Arcturus fat jar into the local Maven repo under the coord the
# plugin's pom declares (com.eu.habbo:Habbo:3.0.0 — same as ms-websockets).
COPY --from=emu-build /emulator.jar /tmp/emulator.jar
RUN mvn install:install-file \
      -Dfile=/tmp/emulator.jar \
      -DgroupId=com.eu.habbo \
      -DartifactId=Habbo \
      -Dversion=3.0.0 \
      -Dpackaging=jar
# Copy plugin source from the monorepo, build.
COPY plugins/pixeltower-rp /src/pixeltower-rp
WORKDIR /src/pixeltower-rp
RUN mvn -B -DskipTests package
RUN cp target/pixeltower-rp.jar /pixeltower-rp.jar

# ── Stage 4: runtime ────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy AS runtime
RUN apt-get update \
  && apt-get install -y --no-install-recommends bash gettext-base tzdata \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /emulator
COPY --from=emu-build               /emulator.jar       /emulator/emulator.jar
COPY --from=plugin-build            /ms-websockets.jar  /emulator/plugins-baked/ms-websockets.jar
COPY --from=pixeltower-plugin-build /pixeltower-rp.jar  /emulator/plugins-baked/pixeltower-rp.jar
COPY docker/emulator/entrypoint.sh  /entrypoint.sh
COPY --chmod=0644 docker/emulator/config.ini.template /emulator/config.ini.template
RUN chmod +x /entrypoint.sh \
  && mkdir -p /emulator/plugins /emulator/logging

EXPOSE 3000 3001 2095

ENTRYPOINT ["/entrypoint.sh"]
