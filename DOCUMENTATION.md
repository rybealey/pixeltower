# Pixeltower

Dockerized Habbo retro stack for `pixeltower.digital` (and `http://localhost` locally).

Containerizes the [duckietm/Complete-Retro-on-Ubuntu](https://github.com/duckietm/Complete-Retro-on-Ubuntu) guide around:

- **CMS**: [ObjectRetros/atomcms](https://github.com/ObjectRetros/atomcms) (PHP 8.4 / Laravel)
- **Emulator**: [Arcturus Morningstar](https://git.krews.org/morningstar/Arcturus-Community) `dev` + [ms-websockets](https://git.krews.org/nitro/ms-websockets) plugin (JRE 17)
- **Client**: [billsonnn/nitro-react](https://github.com/billsonnn/nitro-react) v2 (Node 24)
- **Imager**: [billsonnn/nitro-imager](https://github.com/billsonnn/nitro-imager) (Node 24 + cairo/pango)
- **Converter**: [billsonnn/nitro-converter](https://github.com/billsonnn/nitro-converter) (one-shot, SWF → .nitro)
- **Default assets**: [git.krews.org/nitro/default-assets](https://git.krews.org/nitro/default-assets)
- **Database**: MariaDB 11.8, shared by CMS + emulator
- **Web**: nginx 1.27 mainline, dev/prod templates swapped by `APP_ENV`
- **TLS (prod)**: Cloudflare Origin Certificate (15-year), CF proxy in front

## Layout

```
.
├── docker-compose.yml
├── .env.example              # copy to .env (local) or .env.production
├── docker/
│   ├── php/                  # PHP 8.4-fpm-alpine for AtomCMS
│   ├── emulator/             # Arcturus built from source + ms-websockets plugin
│   ├── imager/               # nitro-imager
│   ├── converter/            # nitro-converter (tools profile)
│   ├── backup/               # nightly mariadb-dump cron
│   └── nginx/                # dev/prod conf.d templates + env-switch entrypoint
├── scripts/                  # bootstrap + pull + seed
├── emulator/                 # config.ini.template, nitrowebsockets-settings.sql (tracked); base-db + plugins/ (gitignored)
├── atomcms/                  # cloned by bootstrap-sources.sh (gitignored)
├── nitro/                    # cloned by bootstrap-sources.sh (gitignored)
├── gamedata/                 # pulled by scripts (gitignored, large)
└── data/                     # persistent host volumes: mariadb, backups, ssl (gitignored)
```

---

## Local bootstrap (first boot)

```bash
cp .env.example .env
# edit DB_PASSWORD and DB_ROOT_PASSWORD to strong random strings

./scripts/bootstrap-sources.sh       # clones AtomCMS, nitro-react, converter, imager, default-assets
./scripts/pull-gamedata.sh           # c_images + sounds from habboassets.com
./scripts/pull-default-pack.sh       # Morningstar default SWF pack + catalog SQLs
./scripts/pull-emulator-sql.sh       # generates emulator/base-database.sql

docker compose up -d db
./scripts/seed-db.sh                 # base + catalog + ws whitelist + console.mode off

./scripts/build-client.sh            # nitro-react build:prod → nitro/dist
./scripts/convert-swfs.sh            # default-pack SWFs → gamedata/*.nitro

./scripts/bootstrap-atom-env.sh
docker compose run --rm php composer install
docker compose run --rm php php artisan key:generate
docker compose run --rm php php artisan storage:link
docker compose run --rm php php artisan migrate --force
docker compose run --rm php php artisan db:seed --force
docker compose --profile tools run --rm atom-builder

docker compose up -d --build
```

Open `http://localhost/`, register an account, click Play. The Nitro client loads at `/client/` and connects to the emulator over `ws://localhost/ws`.

### Smoke test

```bash
curl -I http://localhost/                                    # 200, Laravel
curl -I http://localhost/imaging/?figure=hd-180-1&size=l     # 200, image/png
docker compose logs emulator | grep -iE 'started|listen'     # Arcturus + WS listening
docker compose ps                                             # all services up
```

---

## Production deploy (`pixeltower.digital`, Cloudflare Origin Cert)

This section is the runbook. Everything is ready to execute once a VPS exists and Cloudflare is set up.

### 1. Cloudflare SSL/DNS

1. Add `pixeltower.digital` to Cloudflare. Nameservers point to CF.
2. SSL/TLS → Overview → **Full (strict)**.
3. SSL/TLS → Origin Server → Create Certificate:
   - Hostnames: `pixeltower.digital`, `*.pixeltower.digital`
   - Validity: **15 years**, RSA 2048
4. Save certificate body → `data/ssl/origin.pem`, private key → `data/ssl/origin.key` (both gitignored, `chmod 600`).
5. DNS → create `A` records for `pixeltower.digital` + `www` → VPS IPv4, **proxied** (orange cloud).

### 2. VPS provisioning (Hetzner CPX31 / Ubuntu 24.04)

```bash
# as root, fresh server
apt update && apt upgrade -y
adduser --disabled-password --gecos '' deploy
usermod -aG sudo deploy
mkdir -p /home/deploy/.ssh && chmod 700 /home/deploy/.ssh
# paste your public key into /home/deploy/.ssh/authorized_keys
chown -R deploy:deploy /home/deploy/.ssh && chmod 600 /home/deploy/.ssh/authorized_keys

# Docker
apt install -y ca-certificates curl
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" > /etc/apt/sources.list.d/docker.list
apt update && apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
usermod -aG docker deploy

# firewall (optionally restrict 80/443 to Cloudflare ranges)
ufw allow 22/tcp && ufw allow 80/tcp && ufw allow 443/tcp && ufw --force enable

mkdir -p /opt/pixeltower && chown deploy:deploy /opt/pixeltower
```

### 3. First deploy

```bash
# as deploy@vps
git clone git@github.com:<you>/pixeltower.git /opt/pixeltower
cd /opt/pixeltower

# deliver secrets from your laptop:
#   scp .env.production deploy@vps:/opt/pixeltower/.env.production
#   scp -r data/ssl deploy@vps:/opt/pixeltower/data/ssl
chmod 600 data/ssl/origin.*

# reproduce the local bootstrap, but with --env-file .env.production:
./scripts/bootstrap-sources.sh
./scripts/pull-gamedata.sh
./scripts/pull-default-pack.sh
./scripts/pull-emulator-sql.sh

docker compose --env-file .env.production up -d db
./scripts/seed-db.sh                         # reads .env by default; set ENV_FILE=.env.production

./scripts/build-client.sh
./scripts/convert-swfs.sh

./scripts/bootstrap-atom-env.sh
docker compose --env-file .env.production run --rm php composer install
docker compose --env-file .env.production run --rm php php artisan key:generate
docker compose --env-file .env.production run --rm php php artisan storage:link
docker compose --env-file .env.production run --rm php php artisan migrate --force
docker compose --env-file .env.production run --rm php php artisan db:seed --force
docker compose --env-file .env.production --profile tools run --rm atom-builder

docker compose --env-file .env.production up -d --build
```

### 4. GitHub Actions continuous deploy

Configure repo secrets (Settings → Secrets and variables → Actions):
- `DEPLOY_HOST` — VPS IP or hostname
- `DEPLOY_USER` — `deploy`
- `DEPLOY_SSH_KEY` — PEM-format private key; public half goes in `/home/deploy/.ssh/authorized_keys` on the VPS
- `DEPLOY_PORT` — optional, defaults to 22

Every push to `main` triggers [.github/workflows/deploy.yml](.github/workflows/deploy.yml). The workflow SSHes to the VPS, fast-forwards the repo to `origin/main`, and runs [`scripts/deploy.sh`](scripts/deploy.sh) — the single source of truth for the deploy sequence.

#### How `scripts/deploy.sh` works

Change-aware. Reads `.deploy-sha` (last deployed commit, stored in the repo dir on the VPS), diffs against the newly-checked-out HEAD, and rebuilds only what the diff touched:

| Changed path | Action |
|---|---|
| `nitro-patches/` or `scripts/build-client.sh` | Re-run `scripts/build-client.sh` (Dockerized Vite build of the Nitro client) |
| `atomcms/` | `docker compose --profile tools run --rm atom-builder` (atom + dusk theme rebuild) |
| `plugins/`, `arcturus-patches/`, `docker/emulator/` | `docker compose build emulator` |
| `plugins/*/sql/V*.sql` | Re-run `scripts/seed-db.sh` (idempotent) |

After conditional rebuilds it always runs:
1. `docker compose up -d --remove-orphans` — picks up any new images
2. `php artisan migrate --force` — Laravel migrations
3. Health check — `curl http://localhost/` + `curl http://localhost/imaging/?figure=...`; exits non-zero (turning the Action red) if it can't get a 200 within ~60s

On a green deploy, `.deploy-sha` is updated to the new HEAD. On a red deploy it isn't, so the next green run still diffs against the last known-good commit.

Safety nets baked in:
- **Concurrency lock** — `flock` on `/tmp/pixeltower-deploy.lock` serializes any overlap between the GH Action and a manual `ssh ... ./scripts/deploy.sh`.
- **Pre-deploy DB dump** — `data/backups/pre-deploy/<sha>.sql.gz` written before any migration runs. Last 10 are kept.
- **Logs** — every deploy run tees to `/var/log/pixeltower-deploy.log` (falls back to `/tmp/pixeltower-deploy.log` if the former isn't writable).

#### Operator cheat-sheet

```bash
# Trigger a deploy manually (without a push) — use the Actions tab's
# "Run workflow" button, or push an empty commit:
git commit --allow-empty -m "deploy: manual trigger" && git push

# Force a full rebuild (bypass the diff classifier) via GH Actions UI:
#   Actions → Deploy to production → Run workflow → force_rebuild = true

# Run the deploy manually on the VPS:
ssh deploy@VPS
cd /opt/pixeltower
ENV_FILE=.env.production ./scripts/deploy.sh

# Same, but force full rebuild:
FORCE_REBUILD=1 ENV_FILE=.env.production ./scripts/deploy.sh

# Tail the deploy log:
tail -f /var/log/pixeltower-deploy.log

# Roll back DB to the last pre-deploy snapshot:
ls -1t data/backups/pre-deploy/*.sql.gz | head -n 1   # find most recent
gunzip -c data/backups/pre-deploy/<sha>.sql.gz \
  | docker compose --env-file .env.production exec -T db sh -c \
      'mariadb -uroot -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"'
```

### 5. Cloudflare notes

- WebSocket upgrade works over CF on ports 80/443 by default. This stack uses `/ws` on 443 → supported on Free plan.
- The Origin Cert is valid for 15 years — no renewal cron needed (next rotation: 2041).
- Behind CF, real client IPs arrive via `CF-Connecting-IP`. `docker/nginx/cloudflare-refresh.sh` fetches current CF IP ranges into `docker/nginx/cloudflare.conf`; re-run it annually.

---

## Reference: directly-mapped duckietm guide sections

| duckietm doc | Covered in |
|---|---|
| `VPS_setup.md` | README §2 — provisioning |
| `Atom_setup.md` | `docker/php/`, `scripts/bootstrap-atom-env.sh`, `scripts/pull-emulator-sql.sh` |
| `Imager_And_Gamedata_Setup.md` | `docker/imager/`, `scripts/pull-gamedata.sh`, `scripts/pull-default-pack.sh` |
| `Cloudflare_And_SSL.md` | README §1, `docker/nginx/conf.d/default.prod.conf.template` |
| `Emulator_Setup.md` | `docker/emulator/`, `emulator/config.ini.template`, `scripts/seed-db.sh` |
| `Nitro_and_Settings.md` | `scripts/build-client.sh`, `docker-compose.yml` `nitro-builder` service |
