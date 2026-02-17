CREATE TABLE IF NOT EXISTS archived
(
    trade_uuid      uuid                     NOT NULL PRIMARY KEY,

    trade_timestamp timestamptz DEFAULT NOW() NOT NULL,

    trader_uuid     uuid                     NOT NULL,
    trader_name     varchar(16)              NOT NULL,
    trader_rating   smallint    DEFAULT 0    NOT NULL,
    trader_price    text                     NOT NULL,

    customer_uuid   uuid                     NOT NULL,
    customer_name   varchar(16)              NOT NULL,
    customer_rating smallint    DEFAULT 0    NOT NULL,
    customer_price  text                     NOT NULL,

    trader_items    bytea                    NOT NULL,
    customer_items  bytea                    NOT NULL
);

-- 2. Optimized 'backup' table
CREATE TABLE IF NOT EXISTS backup
(
    trade_uuid uuid                     NOT NULL PRIMARY KEY,
    server_id  int                      NOT NULL,
    timestamp  timestamptz DEFAULT NOW() NOT NULL,
    serialized bytea                    NOT NULL
);

CREATE TABLE IF NOT EXISTS ignored_players
(
    ignorer varchar(16) NOT NULL,
    ignored varchar(16) NOT NULL,
    PRIMARY KEY (ignorer, ignored)
);

CREATE TABLE IF NOT EXISTS player_list
(
    player_name varchar(16) NOT NULL PRIMARY KEY,
    player_uuid uuid        NOT NULL UNIQUE
);