package dev.unnm3d.zelsync.configs;


import de.exlll.configlib.Configuration;
import de.exlll.configlib.YamlConfigurations;

import java.nio.file.Path;
import java.util.List;

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
}
