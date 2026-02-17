CREATE TABLE IF NOT EXISTS archived
(
    trade_uuid      TEXT    NOT NULL PRIMARY KEY,
    trade_timestamp TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    trader_uuid     TEXT    NOT NULL,
    trader_name     TEXT    NOT NULL,
    trader_rating   INTEGER NOT NULL DEFAULT 0,
    trader_price    TEXT    NOT NULL, -- Keep TEXT if formatted, otherwise INTEGER (cents) is better for math

    customer_uuid   TEXT    NOT NULL,
    customer_name   TEXT    NOT NULL,
    customer_rating INTEGER NOT NULL DEFAULT 0,
    customer_price  TEXT    NOT NULL,

    trader_items    BLOB    NOT NULL,
    customer_items  BLOB    NOT NULL
) STRICT;

CREATE TABLE IF NOT EXISTS backup
(
    trade_uuid TEXT    NOT NULL PRIMARY KEY,
    server_id  INTEGER NOT NULL,
    timestamp  TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    serialized BLOB    NOT NULL
) STRICT;

CREATE TABLE IF NOT EXISTS ignored_players
(
    ignorer TEXT NOT NULL,
    ignored TEXT NOT NULL,
    PRIMARY KEY (ignorer, ignored)
) WITHOUT ROWID, STRICT;

CREATE TABLE IF NOT EXISTS player_list
(
    player_name TEXT NOT NULL PRIMARY KEY COLLATE NOCASE,
    player_uuid TEXT NOT NULL
) WITHOUT ROWID, STRICT;

CREATE INDEX IF NOT EXISTS idx_player_uuid ON player_list (player_uuid);