# Brain/Out × Anthill — online match spawning

This directory makes the self-hosted Anthill **game-controller** spawn the
Brain/Out dedicated server as real online matches, on the local `anthill-dev`
stack. It is the core of online-platform Phase 2.

Verified working end-to-end locally: client auth → `POST /create` → master →
controller → Java server spawns, online-inits (server token + discovery),
registers the room → master returns the join location to the client. The room
shows `state=SPAWNED` and the server runs the live freeplay loop.

## Deploy (one command)

`deploy.sh` brings the whole backend up from scratch and is idempotent — it ties
together every step that used to be manual (the upstream stack, keys, the
brainout registration, the scope/FK/market fixes, the JRE-17 controller image,
game-server provisioning, deployment staging and the economy seed).

```bash
# prereq: build the server payload once
./gradlew server:dist make_data

# local
./deploy/anthill/deploy.sh

# real server — clients connect to PUBLIC_HOST
PUBLIC_HOST=203.0.113.10 ./deploy/anthill/deploy.sh
```

It clones `anthill-dev` next to the repo if missing (override `ANTHILL_DIR`),
generates the controller override (JRE-17 image, `gs_host=$PUBLIC_HOST`, a
narrowed `38000-38020` port pool), points the env discovery location and every
discovery `external` address at `$PUBLIC_HOST`, and finishes by verifying
env→discovery. On a real host, open the firewall it prints: **TCP 9500-9518**
and **TCP+UDP 38000-38020** (kryonet needs UDP).

The individual scripts (`provision.sh`, `seed-economy.sh`,
`build-deployment.sh`, `controller.Dockerfile`) are still usable on their own;
the sections below explain the pieces `deploy.sh` orchestrates.

## How spawning works (what we reverse-engineered)

The controller (`anthill-game-controller`) runs a spawned server as a child
process: `<deployment>/<binary> <sock> <ports> <arguments…>` with `cwd` = the
extracted deployment dir, injecting per-room env. The brainout server already
speaks this protocol (`BrainOutServer`): argv = sockets + `tcp,udp,http`, env =
`room_settings`, `server_settings`, `room_id`, `game_max_players`, and — minted
by the master — `login_access_token` and `discovery_services`.

The master fills `login_access_token`/`discovery_services` only if the
`game_servers.game_settings` JSON carries a `token` block (it authenticates a
`dev:` account, non-unique, to mint a server-side token) and a `discover` list
(resolved via discovery). See `provision.sh`.

## Two gotchas that cost real time

1. **No Java in the controller** — the stock controller image is Debian buster
   (Python, no JRE). `controller.Dockerfile` adds a pinned Temurin 17 JRE.
2. **`/tmp` is a noexec tmpfs** — the online controller loads a native JZMQ
   `.so` from `java.io.tmpdir`, which fails to map on noexec. The launcher
   (`anthill-run.sh`) points `-Djava.io.tmpdir=/opt/brainout-tmp` (an exec dir
   baked into the image).

## Steps (local anthill-dev stack)

Prereqs: the anthill-dev stack is up and brainout is registered
(`docs/OnlinePlatformLocal.md`), and `bin/server` is built
(`./gradlew server:dist make_data`).

```bash
cd deploy/anthill

# 1) controller image with a JRE
docker build -f controller.Dockerfile -t brainout/anthill-game-controller:jre17 .
cp docker-compose.override.yml <anthill-dev>/dev/
( cd <anthill-dev>/dev && docker compose up -d --no-build anthill_game_controller )

# 2) stage the deployment into the controller's host-mounted binaries dir
#    (.../runtime/<game>/<version>/<deployment_id>/)
./build-deployment.sh <anthill-dev>/dev/data/game-controller/binaries/runtime/brainout/valpha2/1

# 3) provision the DB (game server/version/deployment + server dev account)
./provision.sh
```

### Trigger a match

```bash
# anonymous player token (scope game)
TOKEN=$(curl -s -X POST http://localhost:9501/auth \
  --data-urlencode credential=anonymous --data-urlencode username=p1 \
  --data-urlencode key=p1 --data-urlencode scopes=game \
  --data-urlencode gamespace=brainout:desktop)

# create + spawn a room (path is game/server/version)
curl -s -X POST http://localhost:9508/create/brainout/free/valpha2 \
  --data-urlencode "access_token=$TOKEN" --data-urlencode "settings={}"
# -> {"location":{"host":"localhost","ports":[..,..,..]},"settings":{...},"id":"N","key":"..."}
```

`docker exec mysql mysql -uroot -pRoot123 dev_game -e "SELECT room_id,state,players FROM rooms"`
should show the room `SPAWNED`; the per-server log is in the controller at
`/var/log/brainout_free_<room>.log*`.

### Matchmaking join (verified)

A second player can matchmake into the **same** live room:

```bash
curl -s -X POST http://localhost:9508/join/brainout/free/valpha2 \
  --data-urlencode "access_token=$TOKEN2" --data-urlencode "settings={}" \
  --data-urlencode "auto_create=false"
# -> same location/ports as the room, a new slot, a per-player key; rooms.players -> 2
```

Note: a spawned room is reaped if no client actually connects (kryonet) within
the slot-reservation grace period — `create`/`join` only reserve a slot, so do
the join promptly when testing by hand.

## Verified with the real desktop client (and what it taught us)

Running the desktop client online against this stack got as far as:
`Auth success → CSGetRegions → CSFindLobby → CSConnecting → [kryonet] Connecting`.
Two real gaps surfaced and are fixed in `provision.sh`:

1. **The client joins a `lobby` first**, not a match — `CSFindLobby` calls
   `joinGame(…, "lobby", auto_create=true)`. With only the `free` server
   provisioned the client died with `404`.
2. **`--map` is a file path**, not a map name: `MapSource` does
   `Gdx.files.absolute(settings.map)`, so `--map lobby` threw
   `Map 'lobby' was not found`; it must be `maps/lobby.map`. (The `free` server
   works because `freeplay-maps.shuffle` *is* a file in the deployment root.)

### Docker Desktop on Windows blocks the last hop

The lobby server spawns and runs (`Server started`, `Inited: {"status":"OK"}`),
but the Windows client cannot open TCP to it. Measured against one live server:

| From | game port 38009 | service port 9501 |
|---|---|---|
| inside the Docker VM (host-network container) | **open** | — |
| Windows host | **refused** | open |

Only `anthill_game_controller` uses `network_mode: host`; every other service is
bridge-networked with an explicit `ports:` mapping (hence 9501 works). Docker
Desktop does not forward a host-network container's **dynamically allocated**
ports (the 38000-40000 game pool) to Windows. Options: enable Docker Desktop's
host-networking feature, narrow `ports_pool_*` and publish that range from a
bridge-networked controller, or run the stack on real Linux — where
`network_mode: host` behaves natively and the problem disappears.

## Not yet done

- A real `deployments` upload/delivery (we pre-stage into the host-mounted
  binaries dir, so the controller skips the download). For multi-host, wire the
  master's deployment storage + controller download instead.
- The actual in-game connection from the GUI client (kryonet handshake with the
  per-player key). The whole platform side — spawn, register, matchmake, join,
  slots, per-player keys — is proven; the netcode join + economy/content for the
  gamespace are the remaining pieces.
