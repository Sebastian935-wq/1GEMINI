package com.example.playerdetector;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class PlayerDetectorClient implements ClientModInitializer {
    public static final String MOD_ID = "player-detector";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final double EXIT_HYSTERESIS_BLOCKS = 3.0;

    private static ModConfig config;
    private static KeyBinding openMenuKey;
    private final Set<UUID> notifiedPlayers = new HashSet<>();

    public static ModConfig getConfig() {
        return config;
    }

    @Override
    public void onInitializeClient() {
        config = ModConfig.load();

        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.player-detector.open_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "key.categories.player-detector"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        LOGGER.info("Player Detector initialized with range={} blocks, enabled={}", config.range, config.enabled);
    }

    private void onClientTick(MinecraftClient client) {
        while (openMenuKey.wasPressed()) {
            if (client.currentScreen == null) {
                client.setScreen(new DetectorScreen(client.currentScreen));
            }
        }

        if (client.world == null || client.player == null) {
            if (!notifiedPlayers.isEmpty()) {
                notifiedPlayers.clear();
            }
            return;
        }

        if (!config.enabled) {
            return;
        }

        double maxRange = config.range;
        double exitRange = maxRange + EXIT_HYSTERESIS_BLOCKS;

        List<? extends AbstractClientPlayerEntity> players = client.world.getPlayers();
        Set<UUID> onlineUuids = new HashSet<>();

        for (AbstractClientPlayerEntity otherPlayer : players) {
            if (otherPlayer == null || otherPlayer == client.player || otherPlayer.getUuid().equals(client.player.getUuid())) {
                continue;
            }

            UUID otherUuid = otherPlayer.getUuid();
            onlineUuids.add(otherUuid);

            double distance = client.player.distanceTo(otherPlayer);

            if (distance <= maxRange) {
                if (!notifiedPlayers.contains(otherUuid)) {
                    notifiedPlayers.add(otherUuid);
                    sendAlert(client, otherPlayer.getName().getString(), distance);
                    playAlertSound(client);
                }
            } else if (distance > exitRange) {
                notifiedPlayers.remove(otherUuid);
            }
        }

        notifiedPlayers.removeIf(uuid -> !onlineUuids.contains(uuid));
    }

    private void sendAlert(MinecraftClient client, String playerName, double distance) {
        if (client.player == null) {
            return;
        }

        String formattedDistance = String.format(Locale.ROOT, "%.2f", distance);

        MutableText message = Text.literal("[PlayerDetector] ")
                .formatted(Formatting.GOLD, Formatting.BOLD)
                .append(Text.literal(playerName).formatted(Formatting.AQUA, Formatting.BOLD))
                .append(Text.literal(" wszedł w zasięg (").formatted(Formatting.YELLOW))
                .append(Text.literal(formattedDistance + " kr.").formatted(Formatting.GREEN, Formatting.BOLD))
                .append(Text.literal(")").formatted(Formatting.YELLOW));

        client.player.sendMessage(message, false);
    }

    public static void playAlertSound(MinecraftClient client) {
        if (client.player == null) {
            return;
        }
        client.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
    }
}
