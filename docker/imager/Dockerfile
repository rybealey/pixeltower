FROM node:20-bookworm

RUN apt-get update && apt-get install -y --no-install-recommends \
      libcairo2-dev libjpeg-dev libpango1.0-dev libgif-dev \
      build-essential g++ \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /src

EXPOSE 3030

CMD ["sh", "-c", "npm install && npm run build && npm start"]
