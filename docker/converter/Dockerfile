FROM node:24-bookworm

RUN apt-get update && apt-get install -y --no-install-recommends \
      build-essential pkg-config \
      libcairo2-dev libpango1.0-dev libjpeg-dev libgif-dev librsvg2-dev \
      ca-certificates git \
  && rm -rf /var/lib/apt/lists/*

RUN corepack enable && npm install -g http-server

WORKDIR /converter
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
