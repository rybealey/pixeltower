FROM node:20-bookworm

WORKDIR /converter

# Build deps for node-canvas / sharp used by nitro-converter
RUN apt-get update && apt-get install -y --no-install-recommends \
      libcairo2-dev libjpeg-dev libpango1.0-dev libgif-dev \
      build-essential g++ python3 \
  && rm -rf /var/lib/apt/lists/*

# Tiny static file server so nitro-converter can fetch SWFs via http://
RUN npm install -g http-server@14

COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
