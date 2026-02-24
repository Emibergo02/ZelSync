package dev.unnm3d.zelsync.configs;


import de.exlll.configlib.Configuration;
import de.exlll.configlib.YamlConfigurationProperties;
import de.exlll.configlib.YamlConfigurations;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

@Configuration
public class GuiSettings {
    private static GuiSettings SETTINGS;

    public static GuiSettings instance() {
        return SETTINGS;
    }

    public static void loadGuiSettings(Path configFile) {
        SETTINGS = YamlConfigurations.update(
          configFile,
          GuiSettings.class,
          YamlConfigurationProperties.newBuilder()
            .header("zelsync guis")
            .footer("Authors: Unnm3d")
            .charset(StandardCharsets.UTF_8)
            .build()
        );
    }

    public static void saveGuiSettings(Path configFile) {
        YamlConfigurations.save(configFile, GuiSettings.class, SETTINGS,
          YamlConfigurationProperties.newBuilder()
            .header("zelsync guis")
            .footer("Authors: Unnm3d")
            .charset(StandardCharsets.UTF_8)
            .build()
        );
    }

    public String playerListTitle = "&6&lPlayer List";

}
