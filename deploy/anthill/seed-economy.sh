#!/usr/bin/env bash
# Seed a starter economy for gamespace 1 (brainout:desktop) into the local
# anthill-dev stack. Idempotent. See docs/OnlineEconomy.md for the model.
#
# NOTE: the desktop client has the IAP store DISABLED
# (DesktopEnvironment.storeEnabled()==false), so on desktop the economy is
# profile-centric — the wallet (gears/skillpts/nuclear-material), level, loadout
# and inventory live in the *profile* service, granted/updated by the game
# server. The store currencies/store below are still authored because Steam /
# Android builds use the IAP store and the profile prices reference them.
set -euo pipefail

MYSQL_CONTAINER="${MYSQL_CONTAINER:-mysql}"
MYSQL_ROOT_PW="${MYSQL_ROOT_PW:-Root123}"
GAMESPACE_ID="${GAMESPACE_ID:-1}"

myq() { docker exec -i "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PW" "$1" 2>/dev/null; }

echo "== dev_store: currencies + default store =="
myq dev_store <<SQL
INSERT INTO currencies (gamespace_id, currency_name, currency_title, currency_format, currency_symbol, currency_label) VALUES
 ($GAMESPACE_ID,'gears','Gears','{0}','gears','gears'),
 ($GAMESPACE_ID,'skillpts','Skill Points','{0}','skillpts','skillpts'),
 ($GAMESPACE_ID,'nuclear-material','Nuclear Material','{0}','nuke','nuclear-material'),
 ($GAMESPACE_ID,'ru','RU','{0}','RU','ru'),
 ($GAMESPACE_ID,'USD','US Dollar','\${0}','\$','usd')
ON DUPLICATE KEY UPDATE currency_title=VALUES(currency_title), currency_symbol=VALUES(currency_symbol), currency_label=VALUES(currency_label);

INSERT INTO stores (gamespace_id, store_name, store_campaign_scheme) VALUES ($GAMESPACE_ID,'main','{}')
ON DUPLICATE KEY UPDATE store_name=VALUES(store_name);
SQL

echo "Done. Currency ids match the client (core/Constants.User): gears, skillpts,"
echo "nuclear-material; plus ru / USD for IAP tiers. Profile wallet/loadout are"
echo "seeded per-account via the profile service (see docs/OnlineEconomy.md)."
