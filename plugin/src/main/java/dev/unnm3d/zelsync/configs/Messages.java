package dev.unnm3d.zelsync.configs;


import de.exlll.configlib.Configuration;
import de.exlll.configlib.YamlConfigurations;

import java.nio.file.Path;

@Configuration
public class Messages {
    private static Messages SETTINGS;
    public static Messages instance() {
        return SETTINGS;
    }

    public static void loadMessages(Path configFile) {
        SETTINGS = YamlConfigurations.update(configFile, Messages.class);
    }


    public String playerNotFound = "<red>Player %player% not found";

    public String playerLocked = "<yellow>You are currently locked due to syncing<br>Please wait until the process is complete";

    public String inventoryLoaded = "<green>Inventory loaded successfully in %time%ms";

}
