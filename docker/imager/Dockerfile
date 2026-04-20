FROM node:24-bookworm

RUN apt-get update && apt-get install -y --no-install-recommends \
      build-essential pkg-config \
      libcairo2-dev libpango1.0-dev libjpeg-dev libgif-dev librsvg2-dev \
      ca-certificates git \
  && rm -rf /var/lib/apt/lists/*

RUN corepack enable

WORKDIR /src

# App source is bind-mounted at runtime (docker/imager/app). On first start,
# install deps and compile; subsequent starts reuse node_modules in the mount.
CMD ["bash", "-lc", "yarn install && yarn build && yarn start"]
