package com.nhulston.essentials.managers;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.nhulston.essentials.util.ConfigManager;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatManager {
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("&([0-9a-fA-F])");
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("&#([0-9a-fA-Fa-f]{6})");

    // Standard Minecraft color codes mapped to hex
    private static final String[] COLOR_MAP = {
            "#000000", // &0 - Black
            "#0000AA", // &1 - Dark Blue
            "#00AA00", // &2 - Dark Green
            "#00AAAA", // &3 - Dark Aqua
            "#AA0000", // &4 - Dark Red
            "#AA00AA", // &5 - Dark Purple
            "#FFAA00", // &6 - Gold
            "#AAAAAA", // &7 - Gray
            "#555555", // &8 - Dark Gray
            "#5555FF", // &9 - Blue
            "#55FF55", // &a - Green
            "#55FFFF", // &b - Aqua
            "#FF5555", // &c - Red
            "#FF55FF", // &d - Light Purple
            "#FFFF55", // &e - Yellow
            "#FFFFFF"  // &f - White
    };

    private final ConfigManager configManager;

    public ChatManager(@Nonnull ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Creates a custom formatter for the PlayerChatEvent based on player's groups.
     */
    @Nonnull
    public PlayerChatEvent.Formatter createFormatter() {
        return this::formatMessage;
    }

    /**
     * Formats a chat message for a player based on their permission groups.
     */
    @Nonnull
    public Message formatMessage(@Nonnull PlayerRef sender, @Nonnull String content) {
        String format = getFormatForPlayer(sender.getUuid());
        String formatted = format
                .replace("%player%", sender.getUsername())
                .replace("%message%", content);

        return parseColorCodes(formatted);
    }

    /**
     * Gets the appropriate chat format for a player based on their permission groups.
     * Returns the first matching group format, or the fallback if no groups match.
     */
    @Nonnull
    private String getFormatForPlayer(@Nonnull UUID playerUuid) {
        Map<String, String> formats = configManager.getChatFormats();

        if (formats.isEmpty()) {
            return configManager.getChatFallbackFormat();
        }

        Set<String> playerGroups = PermissionsModule.get().getGroupsForUser(playerUuid);

        // Check each configured format in order (LinkedHashMap preserves insertion order)
        for (Map.Entry<String, String> entry : formats.entrySet()) {
            String groupName = entry.getKey();
            // Check if player is in this group (case-insensitive)
            for (String playerGroup : playerGroups) {
                if (playerGroup.equalsIgnoreCase(groupName)) {
                    return entry.getValue();
                }
            }
        }

        return configManager.getChatFallbackFormat();
    }

    /**
     * Parses color codes in a string and returns a formatted Message.
     * Supports &0-&9, &a-&f and &#RRGGBB formats.
     */
    @Nonnull
    private Message parseColorCodes(@Nonnull String text) {
        // First, convert all color codes to a normalized format
        // Replace &X with &#XXXXXX (hex equivalent)
        String normalized = text;

        // Replace standard color codes (&0-&f) with hex equivalents
        Matcher colorMatcher = COLOR_CODE_PATTERN.matcher(normalized);
        StringBuilder sb = new StringBuilder();
        while (colorMatcher.find()) {
            String code = colorMatcher.group(1).toLowerCase();
            int index = Character.digit(code.charAt(0), 16);
            String hex = COLOR_MAP[index];
            colorMatcher.appendReplacement(sb, "&#" + hex.substring(1));
        }
        colorMatcher.appendTail(sb);
        normalized = sb.toString();

        // Now parse the string with hex color codes
        List<Message> parts = new ArrayList<>();
        Matcher hexMatcher = HEX_COLOR_PATTERN.matcher(normalized);
        int lastEnd = 0;
        String currentColor = "#FFFFFF";

        while (hexMatcher.find()) {
            // Add text before this color code
            if (hexMatcher.start() > lastEnd) {
                String segment = normalized.substring(lastEnd, hexMatcher.start());
                if (!segment.isEmpty()) {
                    parts.add(Message.raw(segment).color(currentColor));
                }
            }

            // Update current color
            currentColor = "#" + hexMatcher.group(1).toUpperCase();
            lastEnd = hexMatcher.end();
        }

        // Add remaining text
        if (lastEnd < normalized.length()) {
            String segment = normalized.substring(lastEnd);
            if (!segment.isEmpty()) {
                parts.add(Message.raw(segment).color(currentColor));
            }
        }

        if (parts.isEmpty()) {
            return Message.raw(text);
        } else if (parts.size() == 1) {
            return parts.getFirst();
        } else {
            return Message.join(parts.toArray(new Message[0]));
        }
    }

    /**
     * Checks if chat formatting is enabled.
     */
    public boolean isEnabled() {
        return configManager.isChatEnabled();
    }
}
