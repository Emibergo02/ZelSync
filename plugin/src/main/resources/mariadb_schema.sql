CREATE TABLE IF NOT EXISTS player_list
(
    player_uuid UUID        NOT NULL PRIMARY KEY,
    player_name VARCHAR(16) NOT NULL,
    INDEX idx_player_name (player_name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS archived
(
    trade_uuid      UUID             NOT NULL PRIMARY KEY DEFAULT uuid(),
    trade_timestamp TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    trader_uuid     UUID             NOT NULL,
    trader_name     VARCHAR(16)      NOT NULL, -- Kept for historical record
    trader_rating   TINYINT UNSIGNED NOT NULL DEFAULT 0,
    trader_price    TEXT             NOT NULL, -- Consider JSON if structured

    customer_uuid   UUID             NOT NULL,
    customer_name   VARCHAR(16)      NOT NULL, -- Kept for historical record
    customer_rating TINYINT UNSIGNED NOT NULL DEFAULT 0,
    customer_price  TEXT             NOT NULL,

    trader_items    MEDIUMBLOB       NOT NULL,
    customer_items  MEDIUMBLOB       NOT NULL,

    INDEX idx_trade_time (trade_timestamp),
    -- UUID indexes are now much faster due to 16-byte storage
    INDEX idx_trader (trader_uuid),
    INDEX idx_customer (customer_uuid),

    CONSTRAINT fk_archived_trader
        FOREIGN KEY (trader_uuid) REFERENCES player_list (player_uuid)
            ON UPDATE CASCADE,

    CONSTRAINT fk_archived_customer
        FOREIGN KEY (customer_uuid) REFERENCES player_list (player_uuid)
            ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS backup
(
    trade_uuid UUID       NOT NULL PRIMARY KEY,
    server_id  INT        NOT NULL,
    timestamp  TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    serialized LONGBLOB   NOT NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS ignored_players
(
    ignorer_uuid UUID NOT NULL,
    ignored_uuid UUID NOT NULL,

    PRIMARY KEY (ignorer_uuid, ignored_uuid),

    CONSTRAINT fk_ignore_ignorer
        FOREIGN KEY (ignorer_uuid) REFERENCES player_list (player_uuid)
            ON DELETE CASCADE,

    CONSTRAINT fk_ignore_target
        FOREIGN KEY (ignored_uuid) REFERENCES player_list (player_uuid)
            ON DELETE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;