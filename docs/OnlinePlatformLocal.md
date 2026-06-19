# Online Platform — Local Anthill Deployment (status & runbook)

Track B, executing locally with Docker. This records the state of the
self-hosted Anthill backend stood up on the dev machine, the fixes applied, the
remaining blockers, and the brainout-side integration still needed.

## What is deployed

Anthill is brought up from the upstream **anthill-dev** compose (not vendored
into this repo — cloned alongside it):

```bash
cd <somewhere outside this repo>
git clone https://github.com/anthill-platform/anthill-dev.git
cd anthill-dev/dev
docker compose pull          # prebuilt anthillplatform/anthill-*:latest images
docker compose up -d
```

Result: infra (mysql 5.7, redis, rabbitmq, influxdb) + ~17/19 services
**running**: environment, discovery, login, profile, admin, leaderboard, message,
social, event, blog, config, dlc, exec, game(master), promo, report, static.

Endpoints (host): environment `:9503`, discovery `:9502`, login `:9501`,
profile `:9512`, admin UI `:9500` (login `root` / `anthill`).

**Good news on compatibility:** a service logged `API version is '0.2'`, which
matches `anthill-runtime-java 0.2.10` used by the game — so the protocol version
lines up.

## Fixes applied to get the stack up

1. **CRLF line endings** — cloning on Windows gave `dev/keys/setup.sh` (and the
   collectd script) CRLF, so the keygen crashed (`syntax error ... expecting
   "then"`). Fixed: `find . -name '*.sh' -not -path './.git/*' -exec sed -i
   's/\r$//' {} +`, then `docker compose build anthill_keygen`.
2. **Keys** — keygen then generated `dev/keys/private/anthill.pem` and
   `dev/keys/public/anthill.pub`; the services that crash-looped on the missing
   keys recovered.

## Remaining backend blockers

- **store** crash-loops: tables auto-create on startup, but `orders` fails with
  *"Cannot add foreign key constraint"* on MySQL 5.7 (upstream schema/FK-order
  issue). Workaround needed (fix the FK / engine/charset, or create `orders`
  without the constraint). store is in the client `DISCOVER` list.
- **game_controller** ↔ **game_master**: connects then "Lost connection,
  reconnecting in 5s" — controller config (host/region/master auth). Needed for
  the Phase-2 spawn model.
- **No `market` service**: anthill-dev provides login/profile/store/social/etc.
  but **not** `market`, which the game's `Constants.Connection.DISCOVER`
  requires. Options: stub a market service, or drop `MarketService.ID` from the
  client DISCOVER list (loses trading/real-estate features).

## Brainout-side integration still needed

1. **`ENV_SERVICE` is now configurable** (done): `core/Version.java` reads the
   `brainout.env_service` system property or `BRAINOUT_ENV_SERVICE` env var,
   defaulting to the official endpoint. Point client+server at the local
   environment service, e.g. run with `-Dbrainout.env_service=http://localhost:9503`
   (desktop/server) or `BRAINOUT_ENV_SERVICE=http://<host>:9503`. (Android online
   build would wire this through BuildConfig.)
2. **Configure the game in the platform** via the admin UI: the `brainout`
   application / `brainout:desktop` gamespace, application keys, environment
   discovery location, and seed content (store/economy — authored, see
   OnlinePlatform.md).
3. **Run without `--offline`** and validate env→discovery→login→profile, then the
   rest. Expect to iterate on the `market` gap and store FK first.

## Risk / reality

- Protocol version matches (0.2), which removes the biggest unknown, but full
  game-online still needs: store FK fixed, the market gap resolved, the
  gamespace + economy configured, and `ENV_SERVICE` repointed + rebuild. This is
  a multi-session integration effort.
- The Anthill stack is heavy (~20 containers); `docker compose down` to stop.

## Resume checklist

- [ ] Fix store `orders` FK (or recreate without constraint).
- [ ] Resolve the missing `market` service (stub or drop from DISCOVER).
- [ ] Configure `game_controller` → `game_master`.
- [x] Make `ENV_SERVICE` configurable (`-Dbrainout.env_service` / `BRAINOUT_ENV_SERVICE`).
- [ ] Admin: create `brainout` app / `brainout:desktop` gamespace + keys.
- [ ] Run client/server online (no `--offline`); verify login + profile persistence.
- [ ] Seed economy/content for store/events/battlepass.
