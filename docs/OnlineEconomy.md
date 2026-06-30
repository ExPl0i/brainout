# Online economy & content (M4) — model and plan

Authoring the economy/content for the self-hosted gamespace `brainout:desktop`.
Track B / Roadmap M4. This captures the **model** (which turned out to be
profile-centric for desktop) and the concrete plan, grounded in the client/server
code and the Anthill content services.

## The key finding: desktop economy is profile-centric

`DesktopEnvironment.storeEnabled()` returns **false** — the IAP (real-money)
**store is disabled on desktop**. So on the desktop client the economy the player
sees is the **profile**: the wallet, level, loadout and inventory, persisted in
the Anthill **profile** service and granted/updated by the game server during
play. The **store** service (currencies/items/tiers) matters for the **Steam /
Android** builds (which enable IAP) and as the price reference.

So M4 splits in two:

| Piece | Lives in | Who fills it | Desktop impact |
|---|---|---|---|
| Wallet / loadout / inventory / level | **profile** service (`account_profiles.payload` JSON) | game server (on join + during play) | **high** — the menu/UserPanel |
| Currencies, store items, tiers, campaigns | **store** service (`dev_store`) | authored | Steam/Android IAP only |

## Currencies (match the client)

The client's currency ids are in `core/Constants.User`:
`gears` (soft), `skillpts` (skill), `nuclear-material` (premium); plus `ru` and
`USD` for IAP tiers. The profile stores balances under `stats` (e.g.
`stats.gears`). Seeded by `deploy/anthill/seed-economy.sh` into `dev_store`.

## The gap to close first: new online players get an EMPTY profile

When a player joins, the server fetches their profile
(`PlayerClient` → `profileService.getMyProfile`). On `notFound` (a brand-new
anonymous account) it calls `newProfile()` → `setProfile(null)` — an **empty**
profile. Offline, by contrast, `setupOfflineProfile()` applies the rich
`bin/server/default-profile.json` (starting loadout + `stats.gears/skillpts/
nuclear-material`). So a new **online** player has no wallet, no loadout — the
menu shows nothing.

**Fix (DONE — first M4 implementation step):** on the online `notFound` branch,
apply a **starter profile** instead of a blank one (mirrors offline).

1. Authored [`bin/server/starter-profile.json`](../bin/server/starter-profile.json)
   — a clean starting kit (NOT the test `default-profile.json`, which carried
   debug stats like `durability-of-weapon-mp5: 4.997399` and `name: "fefe"`):
   `level 1`, `layout-2`, the default loadout (mp5 / makarov / knife / smoke /
   green skin), `items {sl-grn-smoke:1}`, empty `trophies`/`limits`.
2. `PlayerClient.setupStarterProfile()` loads it on the online `notFound` branch
   (falling back to a blank profile if the file is missing) and marks it dirty so
   it persists to the profile service on first write.

**Starting kit (chosen — "generous"):** `gears 5000`, `skillpts 500`,
`nuclear-material 100` — enough to immediately try upgrades/unlocks and exercise
the menu/store UI. Tune the amounts in `starter-profile.json` to rebalance.

Built (`server:dist`) and restaged into the deployment; a regression spawn still
reaches `SPAWNED`. The profile is applied when a player **connects** to the game
server (kryonet), so populated-profile verification rides on the GUI client join.

## Store catalog (Steam/Android / later)

For the IAP builds, author in `dev_store`: `stores` (done: `main`),
`store_components` (payment method, e.g. an offline/test component),
`items` + `tiers` + tier prices per currency, optional `categories` and
`campaigns`. The client lists a store via `IAP.GetStore(storeName, …)` and reads
tiers/prices; items reference in-game content ids from the packages.

## Verification

- Currencies/store: query `dev_store` (seed script prints them) or the store
  service API.
- Profile: after the starter-profile wiring, a fresh online player's profile in
  `dev_profile.account_profiles` should carry the starting `stats`/`slots`; the
  client UserPanel shows the wallet/loadout instead of empty.

## Status

- [x] Currencies (`gears`/`skillpts`/`nuclear-material`/`ru`/`USD`) + `main`
      store seeded (`deploy/anthill/seed-economy.sh`).
- [x] Decide the new-player starting kit — **generous** (5000/500/100).
- [x] Author `starter-profile.json`; wire the online `notFound` branch to it
      (`PlayerClient.setupStarterProfile`).
- [x] Rebuild `server:dist`, restage the deployment (regression spawn OK).
- [ ] Verify a fresh player's profile is populated — needs the GUI client join
      (player connects → `notFound` → starter profile written to profile service).
- [ ] Store catalog (items/tiers/prices) for Steam/Android IAP.
