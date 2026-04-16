FROM php:8.4-fpm-alpine

ARG UID=1000
ARG GID=1000

RUN apk add --no-cache \
      git curl unzip bash icu-dev libpng-dev libjpeg-turbo-dev libwebp-dev \
      freetype-dev zlib-dev libzip-dev oniguruma-dev mariadb-client \
  && curl -sSLf https://github.com/mlocati/docker-php-extension-installer/releases/latest/download/install-php-extensions \
       -o /usr/local/bin/install-php-extensions \
  && chmod +x /usr/local/bin/install-php-extensions \
  && install-php-extensions pdo_mysql gd bcmath zip intl exif pcntl opcache redis sockets

COPY --from=composer:2 /usr/bin/composer /usr/local/bin/composer
COPY php.ini /usr/local/etc/php/conf.d/zz-app.ini

RUN set -eux; \
    if getent group "${GID}" >/dev/null; then \
      existing_group="$(getent group "${GID}" | cut -d: -f1)"; \
      [ "$existing_group" != "app" ] && addgroup app || true; \
    else \
      addgroup -g "${GID}" app; \
    fi; \
    if getent passwd "${UID}" >/dev/null; then \
      existing_user="$(getent passwd "${UID}" | cut -d: -f1)"; \
      [ "$existing_user" != "app" ] && adduser -D -u "${UID}" -G "$(getent group ${GID} | cut -d: -f1)" app || true; \
    else \
      adduser -D -u "${UID}" -G "$(getent group ${GID} | cut -d: -f1)" app; \
    fi

WORKDIR /var/www/atomcms
USER app
