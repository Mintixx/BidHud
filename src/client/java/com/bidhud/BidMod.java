package com.bidhud;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class BidMod implements ClientModInitializer {
    private static boolean showingRawChat;
    private static KeyMapping menuKey;

    public static String menuKeyLabel() {
        return menuKey == null ? "Right Shift" : menuKey.getTranslatedKeyMessage().getString();
    }

    public static boolean menuKeyPressed(KeyEvent event) {
        if (menuKey == null || !menuKey.matches(event)) return false;
        while (menuKey.consumeClick()) {}
        return true;
    }

    public static void say(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("\u00a76[Bid] \u00a7f" + s));
    }

    public static void info(String s) {
        if (BidConfig.get().localMessages) say(s);
    }

    public static void chat(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.connection != null) mc.player.connection.sendChat(msg);
    }

    public static void command(String command) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.connection != null) mc.player.connection.sendCommand(command);
    }

    public static void play(SoundEvent ev, float pitch, boolean enabled) {
        if (!enabled) return;
        float vol = BidConfig.get().volume / 100f;
        if (vol <= 0) return;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ev, pitch, vol));
    }

    public static void openMenu() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreenAndShow(new BidMenuScreen(null)));
    }

    private static void toggleMenu() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            Screen current = mc.gui.screen();
            if (current instanceof BidMenuScreen || current instanceof HudEditorScreen) {
                current.onClose();
            } else {
                mc.setScreenAndShow(new BidMenuScreen(current));
            }
        });
    }

    @Override
    public void onInitializeClient() {
        BidConfig.load();
        menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.bidhud.menu", InputConstants.Type.KEYSYM, InputConstants.KEY_RSHIFT, KeyMapping.Category.MISC));

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bidhud", "auction_hud"), (ctx, tick) -> {
            BidHud.render(ctx, false);
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            Auction.tick();
            while (menuKey.consumeClick()) toggleMenu();
        });

        ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
            String line = msg.getString();
            BidConfig c = BidConfig.get();
            if (c.debugChat && !showingRawChat) {
                showingRawChat = true;
                try {
                    say("\u00a78RAW: " + line);
                } finally {
                    showingRawChat = false;
                }
            }
            BidConfig.Payment payment = c.parsePayment(line);
            if (payment != null) Auction.onBid(payment.player(), payment.amount());
        });

        ClientCommandRegistrationCallback.EVENT.register((d, reg) -> d.register(
            literal("bid")
                .executes(c -> { openMenu(); return 1; })
                .then(literal("menu").executes(c -> { openMenu(); return 1; }))
                .then(literal("start")
                    .executes(c -> { Auction.start(); return 1; })
                    .then(argument("name", StringArgumentType.greedyString())
                        .executes(c -> start(c.getSource(), StringArgumentType.getString(c, "name")))))
                .then(literal("end").executes(c -> { Auction.finish(false); return 1; }))
                .then(literal("cancel").executes(c -> { Auction.finish(true); return 1; }))
                .then(literal("edit").executes(c -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> mc.setScreenAndShow(new HudEditorScreen(null)));
                    return 1;
                }))
                .then(literal("reset").executes(c -> {
                    BidConfig.get().resetAll();
                    c.getSource().sendFeedback(Component.literal("Everything reset to defaults."));
                    return 1;
                }))
                .then(literal("test")
                    .then(argument("player", StringArgumentType.word())
                        .then(argument("amount", StringArgumentType.word()).executes(c -> {
                            long v = Auction.parse(StringArgumentType.getString(c, "amount"));
                            if (v <= 0) { c.getSource().sendError(Component.literal("Bad amount")); return 0; }
                            Auction.onBid(StringArgumentType.getString(c, "player"), v);
                            return 1;
                        }))))
        ));
    }

    private int start(FabricClientCommandSource src, String name) {
        ItemStack s = Auction.selectedStack();
        Auction.start(name, s);
        return 1;
    }
}
