package com.nhulston.essentials.util;

import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class ConfigManager {
    private static final int DEFAULT_MAX_HOMES = 5;
    private static final String DEFAULT_CHAT_FORMAT = "&7%player%&f: %message%";

    private final Path configPath;
    private int maxHomes = DEFAULT_MAX_HOMES;

    // Chat settings
    private boolean chatEnabled = true;
    private String chatFallbackFormat = DEFAULT_CHAT_FORMAT;
    private final LinkedHashMap<String, String> chatFormats = new LinkedHashMap<>();

    public ConfigManager(@Nonnull Path dataFolder) {
        this.configPath = dataFolder.resolve("config.toml");
        load();
    }

    private void load() {
        if (!Files.exists(configPath)) {
            createDefault();
        }

        try {
            TomlParseResult config = Toml.parse(configPath);

            if (config.hasErrors()) {
                config.errors().forEach(error -> Log.error("Config error: " + error.toString()));
                Log.warning("Using default config values due to errors.");
                return;
            }

            // Homes config
            maxHomes = Math.toIntExact(config.getLong("homes.max-homes", () -> (long) DEFAULT_MAX_HOMES));

            // Chat config
            chatEnabled = config.getBoolean("chat.enabled", () -> true);
            chatFallbackFormat = config.getString("chat.fallback-format", () -> DEFAULT_CHAT_FORMAT);

            // Load chat formats (preserve order for priority)
            chatFormats.clear();
            TomlTable formatsTable = config.getTable("chat.formats");
            if (formatsTable != null) {
                for (String group : formatsTable.keySet()) {
                    String format = formatsTable.getString(group);
                    if (format != null) {
                        chatFormats.put(group.toLowerCase(), format);
                    }
                }
            }

            Log.info("Config loaded: maxHomes=" + maxHomes + ", chatEnabled=" + chatEnabled +
                    ", chatFormats=" + chatFormats.size());
        } catch (IOException e) {
            Log.error("Failed to load config: " + e.getMessage());
            Log.warning("Using default config values.");
        }
    }

    private void createDefault() {
        try {
            Files.createDirectories(configPath.getParent());

            try (InputStream is = getClass().getClassLoader().getResourceAsStream("config.toml")) {
                if (is != null) {
                    Files.copy(is, configPath);
                    Log.info("Created default config.");
                    return;
                }
            }

            Log.error("Could not find config.toml in resources.");
        } catch (IOException e) {
            Log.error("Failed to create default config: " + e.getMessage());
        }
    }

    public int getMaxHomes() {
        return maxHomes;
    }

    public boolean isChatEnabled() {
        return chatEnabled;
    }

    @Nonnull
    public String getChatFallbackFormat() {
        return chatFallbackFormat;
    }

    @Nonnull
    public Map<String, String> getChatFormats() {
        return chatFormats;
    }
}
