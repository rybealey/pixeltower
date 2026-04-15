# Pixelworld.digital — Dockerized Habbo Retro

A fully containerized AtomCMS + Arcturus Morningstar + Nitro stack, runnable locally
(`http://localhost`) and in production on `pixelworld.digital` / `www.pixelworld.digital`
with Let's Encrypt TLS.

Based on the [duckietm/Complete-Retro-on-Ubuntu](https://github.com/duckietm/Complete-Retro-on-Ubuntu)
tutorial, but replaces the bare-metal Ubuntu setup with a single `docker-compose` project.

## Services

| Service | Purpose | Public |
|---|---|---|
| `nginx` | Reverse proxy, static assets, TLS termination | 80, 443 |
| `php` (8.4-fpm) | AtomCMS Laravel runtime | — |
| `db` (MariaDB 11) | Database for CMS + emulator | — |
| `emulator` (Arcturus, JRE 17) | Game server + WebSocket | WS via nginx `/ws` |
| `imager` (Node 23) | Avatar thumbnail generator | via nginx `/imager/` |
| `certbot` | TLS cert issuance + renewal (prod only) | — |

## Prerequisites

- Docker Desktop (local) or Docker Engine + compose plugin (VPS)
- Host UID/GID 1000 is assumed; override `UID`/`GID` in `.env` if different

## One-time asset sourcing

These aren't in the repo — you drop them in before first boot.

1. **AtomCMS** — Laravel source
   ```bash
   git clone https://github.com/sadnessRBLX/AtomCMS atomcms
   ```
2. **Arcturus Morningstar emulator** — compiled JAR + plugins
   - Grab `Emulator.jar` and the `plugins/` folder from the tutorial repo's
     `Emulator_Compiled/` directory, or build from
     [git.krews.org/morningstar/Arcturus-Community](https://git.krews.org/morningstar/Arcturus-Community).
   - Place them into `./emulator/` (so you have `./emulator/Emulator.jar` and `./emulator/plugins/`).
3. **Nitro client** — build once with Node:
   ```bash
   git clone https://git.krews.org/nitro/nitro-react nitro
   cd nitro
   # Edit public/renderer-config.json + ui-config.json, replacing ###YOUR DOMAIN### with
   # http://localhost (dev) or https://pixelworld.digital (prod). Also ensure index.html has
   # <base href="/client/">.
   docker run --rm -v "$PWD":/app -w /app node:20 bash -lc "yarn install && yarn build"
   cd ..
   ```
4. **Gamedata + c_images** — populate `./gamedata/{effect,clothes,furniture,pets,icons,sounds,badges,c_images}` using the
   [Nitro Converter](https://git.krews.org/nitro/nitro-converter). `c_images` also needs a
   full pack (source from retro communities).
5. **Imager source**
   ```bash
   git clone https://github.com/duckietm/Complete-Retro-on-Ubuntu tmp
   cp -r tmp/Docker/imager ./docker/imager/app
   rm -rf tmp
   ```

## Local bootstrap

```bash
cp .env.example .env
# Edit .env — keep APP_ENV=local, set DB passwords, TinyMCE/Turnstile keys optional
docker compose up -d db
docker compose run --rm php composer install
docker compose run --rm php php artisan key:generate
docker compose run --rm php php artisan migrate --seed
docker compose up -d
# Visit http://localhost
```

Emulator logs: `docker compose logs -f emulator`.

## Production bootstrap (on VPS, once only)

Assumed path on the server: `/opt/pixeltower`. DNS: `pixelworld.digital` and
`www.pixelworld.digital` A-records point at the VPS public IP.

```bash
ssh deploy@pixelworld.digital
sudo mkdir -p /opt/pixeltower && sudo chown $USER /opt/pixeltower
cd /opt/pixeltower
git clone git@github.com:<you>/pixeltower.git .

# Copy all assets onto the server (scp / rsync atomcms/, nitro/dist/, emulator/, gamedata/, docker/imager/app/)

cp .env.example .env.production
# Edit: APP_ENV=production, DOMAIN=pixelworld.digital, APP_URL=https://pixelworld.digital,
#       strong DB passwords, LETSENCRYPT_EMAIL, production IMAGER_URL/STATIC_URL/CLIENT_URL,
#       APP_DEBUG=false
ln -sf .env.production .env

# 1. Boot nginx on :80 only, so certbot's webroot challenge can reach us.
docker compose up -d nginx
# 2. Issue the cert (both apex + www on the same cert).
docker compose --profile certbot run --rm certbot certonly \
  --webroot -w /var/www/certbot \
  -d pixelworld.digital -d www.pixelworld.digital \
  --email "$(grep LETSENCRYPT_EMAIL .env | cut -d= -f2)" \
  --agree-tos --non-interactive
# 3. Recreate nginx so it picks up the cert, then bring everything else up.
docker compose up -d --force-recreate nginx
docker compose up -d

# 4. Cert renewal: cron (e.g. weekly) on the host:
#    0 3 * * 1 cd /opt/pixeltower && docker compose --profile certbot run --rm certbot renew --webroot -w /var/www/certbot && docker compose exec nginx nginx -s reload
```

## GitHub-based deployment

`.github/workflows/deploy.yml` SSHes into the VPS on every push to `main` and runs
`git pull && docker compose up -d --build`. Required repo secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `DEPLOY_HOST` | VPS IP or `pixelworld.digital` |
| `DEPLOY_USER` | SSH user (e.g. `deploy`) |
| `DEPLOY_SSH_KEY` | Private key content for a dedicated deploy key |
| `DEPLOY_PORT` | (optional) SSH port, default 22 |

On the VPS, add the matching public key to `~/.ssh/authorized_keys` for the deploy user,
and give that user permission to run `docker` (usually: `usermod -aG docker deploy`).

## Recommended VPS

Hetzner Cloud **CPX31** (4 vCPU / 8 GB / 160 GB NVMe, ~€8/mo) is the sweet spot for this
stack. DigitalOcean / Vultr equivalents also work; avoid PaaS (Railway, Fly.io, Render) —
large persistent volumes and the JVM emulator fight their model.

## Security defaults

- MariaDB + Imager are not exposed to the host — only the compose network reaches them.
- Emulator RCON bound to `127.0.0.1:3001` on the host; never public.
- `.env.production` stays on the VPS (gitignored); only `.env.example` is committed.
- Nightly DB backup (add to VPS crontab):
  ```
  0 4 * * * cd /opt/pixeltower && docker compose exec -T db mariadb-dump -uroot -p"$DB_ROOT_PASSWORD" --all-databases | gzip > /opt/pixeltower/data/backups/db-$(date +\%F).sql.gz
  ```

## Verification

Local:
- `docker compose ps` shows all services up
- `curl -I http://localhost/` → 200
- `curl -I http://localhost/imager/?figure=hr-100` → PNG
- Browse `http://localhost/client/`, create account via AtomCMS, log in

Production:
- `curl -I https://pixelworld.digital/` → 200 with valid TLS
- `curl -I http://pixelworld.digital/` → 301 redirect
- `openssl s_client -connect pixelworld.digital:443 -servername www.pixelworld.digital </dev/null 2>/dev/null | openssl x509 -noout -subject -ext subjectAltName` — SAN covers both hostnames
- In-game WS handshake succeeds on `wss://pixelworld.digital/ws`
