create table if not exists archived
(
    trade_uuid      CHAR(36)         not null primary key,
    trade_timestamp timestamp        not null default CURRENT_TIMESTAMP,

    trader_uuid     CHAR(36)         not null,
    trader_name     VARCHAR(16)      not null,
    trader_rating   TINYINT UNSIGNED not null default 0,
    trader_price    TEXT             not null,

    customer_uuid   CHAR(36)         not null,
    customer_name   VARCHAR(16)      not null,
    customer_rating TINYINT UNSIGNED not null default 0,
    customer_price  TEXT             not null,

    trader_items    mediumblob       not null,
    customer_items  mediumblob       not null,

    INDEX idx_trade_time (trade_timestamp),
    INDEX idx_trader (trader_uuid),
    INDEX idx_customer (customer_uuid),

    CONSTRAINT fk_archived_trader FOREIGN KEY (trader_uuid) REFERENCES player_list (player_uuid),
    CONSTRAINT fk_archived_customer FOREIGN KEY (customer_uuid) REFERENCES player_list (player_uuid)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

create table if not exists backup
(
    trade_uuid CHAR(36)                            not null primary key,
    server_id  int                                 not null,
    timestamp  timestamp default CURRENT_TIMESTAMP not null,
    serialized longblob                            not null
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

create table if not exists ignored_players
(
    ignorer varchar(16) not null,
    ignored varchar(16) not null,
    primary key (ignorer, ignored),

    INDEX idx_ignorer (ignorer),
    INDEX idx_ignored (ignored)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

create table if not exists player_list
(
    player_name varchar(16) not null primary key,
    player_uuid CHAR(36)    not null
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;