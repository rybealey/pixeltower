FROM php:8.4-fpm-alpine

ARG UID=1000
ARG GID=1000

RUN apk add --no-cache \
      bash git curl unzip tzdata linux-headers \
      icu-dev libxml2-dev libzip-dev \
      oniguruma-dev \
      libpng-dev libjpeg-turbo-dev freetype-dev \
      imagemagick imagemagick-dev \
      $PHPIZE_DEPS \
  && docker-php-ext-configure gd --with-freetype --with-jpeg \
  && docker-php-ext-install -j"$(nproc)" \
      bcmath intl mbstring opcache pdo_mysql mysqli soap xml zip gd sockets \
  && pecl install imagick \
  && docker-php-ext-enable imagick \
  && apk del --no-network $PHPIZE_DEPS

COPY php.ini /usr/local/etc/php/conf.d/zz-pixeltower.ini

COPY --from=composer:2 /usr/bin/composer /usr/local/bin/composer

RUN addgroup -g "${GID}" app \
  && adduser -D -u "${UID}" -G app -s /bin/sh app \
  && mkdir -p /var/www/atomcms \
  && chown -R app:app /var/www

WORKDIR /var/www/atomcms
USER app

EXPOSE 9000
CMD ["php-fpm", "-F"]
