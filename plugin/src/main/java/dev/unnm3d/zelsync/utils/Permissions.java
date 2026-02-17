package dev.unnm3d.zelsync.utils;

import lombok.Getter;

@Getter
public enum Permissions {
    MODIFY_TRADE("zeltrade.modify"),
    USE_CURRENCY_PREFIX("zeltrade.usecurrency."),

    ;

    private final String permission;

    Permissions(String permission) {
        this.permission = permission;
    }

}
