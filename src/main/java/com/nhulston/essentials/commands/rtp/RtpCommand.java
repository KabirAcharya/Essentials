package com.nhulston.essentials.commands.rtp;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.nhulston.essentials.managers.TeleportManager;
import com.nhulston.essentials.util.ConfigManager;
import com.nhulston.essentials.util.Msg;
import com.nhulston.essentials.util.TeleportUtil;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Command to randomly teleport a player within a configured radius.
 * Usage: /rtp
 */
public class RtpCommand extends AbstractPlayerCommand {
    private static final int MAX_ATTEMPTS = 5;
    private static final String COOLDOWN_BYPASS_PERMISSION = "essentials.rtp.cooldown.bypass";

    private static final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    private final ConfigManager configManager;
    private final TeleportManager teleportManager;

    public RtpCommand(@Nonnull ConfigManager configManager, @Nonnull TeleportManager teleportManager) {
        super("rtp", "Randomly teleport to a location");
        this.configManager = configManager;
        this.teleportManager = teleportManager;

        requirePermission("essentials.rtp");
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        UUID playerUuid = playerRef.getUuid();

        // Check cooldown (skip if player has bypass permission)
        int cooldownSeconds = configManager.getRtpCooldown();
        boolean bypassCooldown = PermissionsModule.get().hasPermission(playerUuid, COOLDOWN_BYPASS_PERMISSION);
        if (cooldownSeconds > 0 && !bypassCooldown) {
            Long lastUse = cooldowns.get(playerUuid);
            if (lastUse != null) {
                long elapsed = (System.currentTimeMillis() - lastUse) / 1000;
                long remaining = cooldownSeconds - elapsed;
                if (remaining > 0) {
                    Msg.fail(context, "RTP is on cooldown. Please wait " + remaining + " seconds.");
                    return;
                }
            }
        }

        String rtpWorldName = configManager.getRtpWorld();
        int radius = configManager.getRtpRadius();

        // Verify the world exists
        World rtpWorld = Universe.get().getWorld(rtpWorldName);
        if (rtpWorld == null) {
            Msg.fail(context, "RTP world '" + rtpWorldName + "' is not loaded.");
            return;
        }

        // Try up to MAX_ATTEMPTS random locations
        ThreadLocalRandom random = ThreadLocalRandom.current();
        
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            double x = random.nextDouble(-radius, radius);
            double z = random.nextDouble(-radius, radius);

            // Find safe Y position, searching from top down
            Double safeY = TeleportUtil.findSafeRtpY(rtpWorld, x, z);
            
            if (safeY != null) {
                // Found a safe location - set cooldown
                cooldowns.put(playerUuid, System.currentTimeMillis());

                Vector3d startPosition = playerRef.getTransform().getPosition();

                teleportManager.queueTeleport(
                    playerRef, ref, store, startPosition,
                    rtpWorldName, x, safeY, z,
                    0.0f, 0.0f, // yaw, pitch - face forward
                    "Randomly teleported!"
                );
                return;
            }
        }

        // All attempts failed
        Msg.fail(context, "Could not find a safe location after " + MAX_ATTEMPTS + " attempts. Try again.");
    }

    /**
     * Cleans up cooldown for a player when they disconnect.
     */
    public static void onPlayerQuit(UUID playerUuid) {
        cooldowns.remove(playerUuid);
    }
}
