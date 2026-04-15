# Mirrors duckietm/Complete-Retro-on-Ubuntu Docker/Dockerfile.
FROM node:23.11.0

RUN apt-get update && apt-get install -y \
      libcairo2-dev libjpeg-dev libpango1.0-dev libgif-dev \
      build-essential g++ \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /src

EXPOSE 3030

CMD ["sh", "-c", "if [ -f package.json ]; then yarn install --frozen-lockfile && yarn start; else echo 'Drop imager source into ./docker/imager/app/ (clone the imager repo there)'; sleep infinity; fi"]
