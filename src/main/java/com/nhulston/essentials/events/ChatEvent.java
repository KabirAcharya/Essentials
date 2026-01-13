package com.nhulston.essentials.events;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.nhulston.essentials.managers.ChatManager;
import com.nhulston.essentials.util.Log;

import javax.annotation.Nonnull;

public class ChatEvent {
    private final ChatManager chatManager;

    public ChatEvent(@Nonnull ChatManager chatManager) {
        this.chatManager = chatManager;
    }

    public void register(@Nonnull EventRegistry eventRegistry) {
        if (!chatManager.isEnabled()) {
            return;
        }

        eventRegistry.<String, PlayerChatEvent>registerAsyncGlobal(PlayerChatEvent.class, future ->
                future.thenApply(event -> {
                    event.setFormatter(chatManager.createFormatter());
                    return event;
                })
        );
        Log.info("Chat formatting enabled.");
    }
}
