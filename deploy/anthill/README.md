# Brain/Out × Anthill — online match spawning

This directory makes the self-hosted Anthill **game-controller** spawn the
Brain/Out dedicated server as real online matches, on the local `anthill-dev`
stack. It is the core of online-platform Phase 2.

Verified working end-to-end locally: client auth → `POST /create` → master →
controller → Java server spawns, online-inits (server token + discovery),
registers the room → master returns the join location to the client. The room
shows `state=SPAWNED` and the server runs the live freeplay loop.

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

## Not yet done

- A real `deployments` upload/delivery (we pre-stage into the host-mounted
  binaries dir, so the controller skips the download). For multi-host, wire the
  master's deployment storage + controller download instead.
- A client joining the spawned room from the GUI (the API + spawn are proven;
  the in-game join/economy still needs the gamespace content seeded).
