# Pixelworld.digital — Dockerized Habbo Retro

A fully containerized AtomCMS + Arcturus Morningstar + Nitro stack, runnable locally
(`http://localhost`) and in production on `pixelworld.digital` / `www.pixelworld.digital`
with Let's Encrypt TLS. GitHub push → VPS auto-deploy via Actions.

## Services

| Service | Purpose | Public |
|---|---|---|
| `nginx` | Reverse proxy, static assets, TLS termination | 80, 443 |
| `php` (8.4-fpm) | AtomCMS Laravel runtime | — |
| `db` (MariaDB 11) | Database for CMS + emulator | — |
| `emulator` (Arcturus, JRE 17, built from source) | Game server + WebSocket | WS via nginx `/ws` |
| `imager` (Node 23) | Avatar thumbnail generator | via nginx `/imager/` |
| `backup` (cron, MariaDB client) | Nightly DB dumps to `./data/backups/` | — |
| `certbot` (profile `certbot`) | Let's Encrypt cert issuance + renewal | — |
| `converter` (profile `tools`) | Nitro Converter — one-shot SWF → `.nitro` conversion | — |
| `nitro-builder` (profile `tools`) | One-shot `yarn build:prod` in `./nitro/` | — |

## Upstream sources used

- **AtomCMS** — [atom-retros/atomcms](https://github.com/atom-retros/atomcms) (Laravel, PHP 8.4)
- **Arcturus Morningstar** — [git.krews.org/morningstar/Arcturus-Community](https://git.krews.org/morningstar/Arcturus-Community) (built from source in the `emulator` image)
- **Nitro client** — [billsonnn/nitro-react](https://github.com/billsonnn/nitro-react)
- **Nitro Converter** — [billsonnn/nitro-converter](https://github.com/billsonnn/nitro-converter)
- **Imager** — `duckietm/Complete-Retro-on-Ubuntu/Docker/imager` (copied into `docker/imager/app/`)

## One-time asset sourcing

### 1. Clone all third-party sources
All four live outside the main repo (each has its own upstream). One script pulls them all:
```bash
./scripts/bootstrap-sources.sh
```
It clones AtomCMS, nitro-react, nitro-converter, and the imager source into
their expected paths. Re-running is safe (skips existing); delete a dir to re-clone.

### 2. SWF pack (raw flash assets)
Obtain a community-provided SWF pack. Expected layout (verified against
`flash-assets-PRODUCTION-*`):
```
<pack>/
  *.swf                     (3000+ figure/effect/pet/furni SWFs at root)
  figuremap.xml
  effectmap.xml
  HabboAvatarActions.xml
  gamedata/
    figuredata.xml
    furnidata_xml.xml
    productdata.txt
    external_variables.txt
    external_flash_texts.txt
    override/
```

### 3. Convert SWFs → `.nitro` bundles
Point the converter at your pack and run it:
```bash
export SWF_PACK_DIR="$HOME/Downloads/flash-assets-PRODUCTION-202604081915-644373475"
docker compose --profile tools run --rm converter
# Output lands in ./gamedata/ (bundled/, gamedata/, figure/, effect/, furniture/, pet/)
# Runtime: ~10–30 min for ~3000 SWFs
```
The converter spins up a loopback HTTP server inside the container serving
`$SWF_PACK_DIR`, so the upstream `nitro-converter` can fetch over HTTP as it
expects, without network access.

### 4. Build the Nitro client
Edit `nitro/public/renderer-config.json` and `nitro/public/ui-config.json` if
your URLs differ from `http://localhost` (defaults are already localhost-ready).
Then:
```bash
docker compose --profile tools run --rm nitro-builder
# Output: ./nitro/dist/ — nginx serves it at /client/
```

### 5. c_images pack
The SWF pack does **not** include `c_images/` (avatar figureparts for
imager-generated thumbnails + catalogue PNGs). Source a full c_images pack
from retro communities and drop it into `./gamedata/c_images/` (with its
`catalogue/` subfolder).

### 6. Arcturus base SQL dump
Upstream's `sqlupdates/` folder has incremental migrations only — no base
schema. Obtain a `arcturus_3.x.x.sql` base dump from the community, then:
```bash
docker compose up -d db
docker compose exec -T db mariadb -uroot -p"$DB_ROOT_PASSWORD" "$DB_DATABASE" < path/to/arcturus-base.sql
# Then apply updates from /opt/arcturus/sqlupdates inside the emulator image in order, as needed.
```

## Local bootstrap

```bash
cp .env.example .env
# Edit .env — keep APP_ENV=local, set DB passwords
./scripts/bootstrap-atom-env.sh           # produces atomcms/.env from root .env
docker compose up -d db
docker compose run --rm php composer install
docker compose run --rm php php artisan key:generate
docker compose run --rm php php artisan migrate --seed
docker compose up -d
# Visit http://localhost
```

## Production bootstrap (on VPS at /opt/pixeltower)

```bash
# DNS: A records for pixelworld.digital + www → VPS IP
git clone git@github.com:<you>/pixeltower.git /opt/pixeltower
cd /opt/pixeltower
# rsync assets (atomcms/, nitro/dist/, gamedata/, docker/converter/app/, docker/imager/app/) onto the VPS
cp .env.example .env
# Edit: APP_ENV=production, DOMAIN=pixelworld.digital, LETSENCRYPT_EMAIL=..., strong DB passwords, APP_DEBUG=false
./scripts/bootstrap-atom-env.sh

# 1. Boot nginx alone on :80 so ACME challenges can reach us
docker compose up -d nginx
# 2. Issue cert for both apex + www
docker compose --profile certbot run --rm certbot certonly \
  --webroot -w /var/www/certbot \
  -d pixelworld.digital -d www.pixelworld.digital \
  --email "$(grep LETSENCRYPT_EMAIL .env | cut -d= -f2)" \
  --agree-tos --non-interactive
# 3. Recreate nginx with cert + bring up everything else
docker compose up -d --force-recreate nginx
docker compose up -d

# 4. Cert renewal cron (weekly on the VPS):
#    0 3 * * 1 cd /opt/pixeltower && docker compose --profile certbot run --rm certbot renew --webroot -w /var/www/certbot && docker compose exec nginx nginx -s reload
```

Before Nitro loads correctly in production, rebuild the client with prod URLs:
```bash
# Edit nitro/public/renderer-config.json and ui-config.json: replace http://localhost with https://pixelworld.digital
docker compose --profile tools run --rm nitro-builder
```

## GitHub-based deployment

`.github/workflows/deploy.yml` SSHes into the VPS on every push to `main`:
```
git pull && docker compose --env-file .env.production up -d --build
```

Required repo secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `DEPLOY_HOST` | VPS IP or `pixelworld.digital` |
| `DEPLOY_USER` | SSH user (e.g. `deploy`, in the `docker` group) |
| `DEPLOY_SSH_KEY` | Private key content for a dedicated deploy key |
| `DEPLOY_PORT` | (optional) SSH port, default 22 |

## Recommended VPS

Hetzner Cloud **CPX31** (4 vCPU / 8 GB / 160 GB NVMe, ~€8/mo). Ubuntu 24.04.
Avoid PaaS (Railway, Fly.io, Render) — large persistent volumes and the JVM
emulator don't fit their model cleanly.

## Security defaults

- MariaDB, Imager, and Emulator WS are not exposed to the host — only the
  compose network reaches them.
- RCON bound to `127.0.0.1:3001` on the host; never public.
- `.env` (prod) and `atomcms/.env` stay on the VPS (gitignored); only
  `.env.example` is committed.
- Nightly DB backups via the `backup` service to `./data/backups/`.
  Configure via `BACKUP_SCHEDULE`, `BACKUP_RETENTION_DAYS`, `TZ` in `.env`.
  Restore: `gunzip -c data/backups/db-XXXXX.sql.gz | docker compose exec -T db mariadb -uroot -p"$DB_ROOT_PASSWORD"`.

## Verification

**Local:**
- `docker compose ps` — `nginx php db emulator imager backup` all healthy
- `curl -I http://localhost/` → 200
- `curl http://localhost/imager/?figure=hr-100` → PNG
- Visit `http://localhost/client/` — Nitro loads, WS connects on `/ws`

**Production:**
- `curl -I https://pixelworld.digital/` → 200, valid LE cert
- `curl -I http://pixelworld.digital/` → 301
- `openssl s_client -connect pixelworld.digital:443 -servername www.pixelworld.digital </dev/null 2>/dev/null | openssl x509 -noout -ext subjectAltName` — SAN covers both
- In-game login via `https://pixelworld.digital/client/`
