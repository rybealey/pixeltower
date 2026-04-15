FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache bash gettext

WORKDIR /emulator
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

EXPOSE 3000 3001 2095

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
