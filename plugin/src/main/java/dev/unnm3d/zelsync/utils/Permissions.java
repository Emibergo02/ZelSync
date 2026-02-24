package dev.unnm3d.zelsync.utils;

import lombok.Getter;

@Getter
public enum Permissions {
    MODIFY_TRADE("zelsync.modify"),
    USE_CURRENCY_PREFIX("zelsync.usecurrency."),

    ;

    private final String permission;

    Permissions(String permission) {
        this.permission = permission;
    }

}
