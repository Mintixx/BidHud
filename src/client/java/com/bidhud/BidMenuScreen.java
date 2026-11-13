package com.bidhud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import org.joml.Matrix3x2fStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class BidMenuScreen extends Screen {
    private static final String[] TABS = {"Auction", "Layout", "Style", "Colors", "Text", "Elements",
            "Effects", "Chat & sound", "Advanced", "Messages"};
    private static final int SIDE_W = 94, STRIP_H = 64;

    private final Screen parent;
    private int tab = 0;
    private List<Row> rows = new ArrayList<>();
    private List<String> curFields = new ArrayList<>();
    private double scroll;
    private int maxScroll;
    private Row active;
    private int ax, ay, aw;
    private boolean draggingScroll;
    private int scrollGrabOffset;
    private final Set<String> collapsedSections = new HashSet<>();
    private TextRow focus;
    private int pX, pY, pW, pH, cX, cY, cW, cB;
    private String search = "";
    private ItemStack hover = ItemStack.EMPTY;
    private static List<ItemStack> ALL;
    private List<ItemStack> filtered = new ArrayList<>();
    private String filteredFor = null;

    private String sectionKey(Header header) {
        return tab + ":" + rows.indexOf(header);
    }

    private boolean rowVisible(int index) {
        for (int i = index; i >= 0; i--) {
            if (rows.get(i) instanceof Header header) {
                return i == index || !collapsedSections.contains(sectionKey(header));
            }
        }
        return true;
    }

    private void toggleSection(Header header) {
        String key = sectionKey(header);
        if (!collapsedSections.add(key)) collapsedSections.remove(key);
    }

    private int thumbHeight() {
        int trackH = cB - cY;
        return Math.max(14, trackH * trackH / (trackH + maxScroll));
    }

    private int thumbY() {
        int trackH = cB - cY;
        int thumbH = thumbHeight();
        return cY + (int) ((trackH - thumbH) * (scroll / maxScroll));
    }

    private void dragScrollThumb(int mouseY) {
        int trackH = cB - cY;
        int thumbH = thumbHeight();
        int thumbTop = Math.max(cY, Math.min(cB - thumbH, mouseY - scrollGrabOffset));
        scroll = (thumbTop - cY) / (double) Math.max(1, trackH - thumbH) * maxScroll;
    }

    public BidMenuScreen(Screen parent) {
        super(Component.literal("Bid HUD"));
        this.parent = parent;
        setTab(0);
    }

    // ------------------------------------------------------------------ helpers
    Font tr() { return font; }
    int acc() { return 0xFF000000 | (BidConfig.get().cAccent & 0xFFFFFF); }

    static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static boolean isClickButton(int button) {
        return button == 0 || button == 1;
    }

    boolean hov(int x, int y, int w, int h, int mx, int my) {
        return in(mx, my, x, y, w, h) && my >= cY && my < cB && mx >= cX && mx < cX + cW;
    }

    private int sidebarTabY(int index) {
        int top = pY + 32;
        int bottom = pY + pH - 24;
        int spacing = Math.min(22, Math.max(1, (bottom - top) / TABS.length));
        return top + index * spacing;
    }

    private int sidebarTabHeight() {
        int top = pY + 32;
        int bottom = pY + pH - 24;
        int spacing = Math.min(22, Math.max(1, (bottom - top) / TABS.length));
        return Math.min(20, spacing);
    }

    private void stxt(GuiGraphicsExtractor ctx, String s, float x, float y, int col, float sc) {
        Matrix3x2fStack m = ctx.pose();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(sc, sc);
        ctx.text(font, s, 0, 0, col, false);
        m.popMatrix();
    }

    private void button(GuiGraphicsExtractor ctx, int x, int y, int w, int h, String label, boolean hovered, int chip) {
        ctx.fill(x, y, x + w, y + h, hovered ? 0xFF303042 : 0xFF1E1E2A);
        ctx.outline(x, y, w, h, hovered ? acc() : 0xFF36364A);
        int tw = font.width(label);
        int off = chip != 0 ? 7 : 0;
        if (chip != 0) ctx.fill(x + (w - tw) / 2 - 7, y + h / 2 - 3, x + (w - tw) / 2 - 1, y + h / 2 + 3, chip);
        ctx.text(font, label, x + (w - tw) / 2 + off / 2, y + (h - 8) / 2, 0xFFFFFFFF, false);
    }

    // ------------------------------------------------------------------ tabs / building
    private void setTab(int t) {
        clearTextFocus();
        tab = t;
        scroll = 0;
        active = null;
        curFields = new ArrayList<>();
        rows = build(t);
    }

    private void clearTextFocus() {
        if (focus != null && focus.input != null) focus.input.setFocused(false);
        focus = null;
    }

    private void focus(TextRow row) {
        clearTextFocus();
        focus = row;
        if (row.input == null) {
            row.input = new EditBox(font, Component.literal(row.label));
            row.input.setMaxLength(row.max);
            row.input.setBordered(false);
            row.input.setTextColor(0xFFFFFFFF);
            row.input.setResponder(row.st);
        }
        row.input.setValue(row.g.get());
        row.input.setCursorPosition(row.input.getValue().length());
        row.input.setFocused(true);
    }

    private F fld(String n) {
        curFields.add(n);
        return new F(n);
    }

    private Row tog(String label, String f) { F x = fld(f); return new Toggle(label, x::b, x::set); }

    private Row cyc(String label, String f, String... opts) {
        F x = fld(f);
        return new Cycle(label, opts, x::i, v -> x.set(v));
    }

    private Row col(String label, String f) { return new ColorRow(label, fld(f)); }

    private Row txt(String label, String f, int max) {
        F x = fld(f);
        return new TextRow(label, x::s, v -> x.set(v), max);
    }

    private Row sld(String label, String f, double min, double max, double step, String unit) {
        F x = fld(f);
        boolean isInt = x.f.getType() == int.class;
        DoubleSupplier g = isInt ? () -> x.i() : () -> x.fl();
        DoubleConsumer st = v -> x.set(isInt ? (Object) Integer.valueOf((int) Math.round(v)) : (Object) Float.valueOf((float) v));
        DoubleFunction<String> fm = v -> (step >= 1 ? String.valueOf((int) Math.round(v)) : String.format(Locale.ROOT, "%.2f", v)) + unit;
        return new Slider(label, g, st, min, max, step, fm);
    }

    private Runnable preset(int bg, int bg2, int accent, int urg, int text, int sub, int bid, int track) {
        return () -> {
            BidConfig c = BidConfig.get();
            c.cBg = bg; c.cBg2 = bg2; c.cAccent = accent; c.cUrgent = urg; c.cText = text; c.cSub = sub;
            c.cBid = bid; c.cBidder = sub; c.cTimer = text; c.cTrack = track; c.cBorder = accent;
            c.cFlash = accent; c.cSold = bid;
            c.markDirty();
            c.saveIfDirty();
        };
    }

    private List<Row> build(int t) {
        List<Row> r = new ArrayList<>();
        switch (t) {
            case 0 -> {
                r.add(new Header("Item to bid"));
                r.add(tog("Use item in my hand", "useHeld"));
                r.add(new Note("Off = use the item you pick below. You don't have to own it."));
                r.add(sld("Item count", "itemCount", 1, 64, 1, ""));
                r.add(txt("Custom name", "customName", 40));
                r.add(new Header("Pick from your inventory"));
                r.add(new ItemGridRow(this::inventoryStacks, this::pickInventory, true));
                r.add(new Header("Favorited items"));
                r.add(new ItemGridRow(this::favoriteStacks, this::pickInventory, true));
                r.add(new Header("Pick any item in the game"));
                r.add(new TextRow("Search", () -> search, v -> {
                    search = v;
                    filteredFor = null;
                }, 30));
                r.add(new ItemGridRow(this::allStacks, this::pickAny, true));
                r.add(new Header("Auction rules"));
                r.add(sld("Duration", "duration", 10, 600, 5, "s"));
                r.add(sld("Add time per top bid", "bidTimeExtensionSeconds", 0, 60, 1, "s"));
                r.add(tog("Enable stream delay", "streamDelay"));
                r.add(sld("Stream delay length", "streamDelaySeconds", 1, 60, 1, "s"));
                r.add(tog("Refund pay", "refundPay"));
                r.add(sld("Time between refunds", "refundIntervalSeconds", 0.5, 10, 0.5, "s"));
                r.add(new Note("Gap between late-payment refund commands."));
                r.add(txt("Starting bid", "startBid", 12));
                r.add(txt("Minimum raise", "minIncrement", 12));
                r.add(tog("Anti-snipe", "antiSnipe"));
                r.add(sld("Extend to", "antiSnipeSeconds", 2, 30, 1, "s"));
                r.add(txt("Ignore players", "ignoreList", 80));
                r.add(new Note("Ignore list: names separated by commas."));
            }
            case 1 -> {
                r.add(cyc("Menu size", "menuSize", "Compact", "Regular", "Large"));
                r.add(new Header("Position"));
                r.add(cyc("Anchor", "anchor", "Top left", "Top center", "Top right", "Middle left", "Center",
                        "Middle right", "Bottom left", "Bottom center", "Bottom right"));
                r.add(sld("Offset X", "offsetX", -800, 800, 1, "px"));
                r.add(sld("Offset Y", "offsetY", -500, 500, 1, "px"));
                r.add(new ButtonRow(new String[]{"Drag to position", "Center horizontally"}, null, new Runnable[]{
                        () -> Minecraft.getInstance().setScreenAndShow(new HudEditorScreen(this)),
                        () -> {
                            BidConfig c = BidConfig.get();
                            c.anchor = (c.anchor / 3) * 3 + 1;
                            c.offsetX = 0;
                            c.markDirty();
                        }}));
                r.add(new Header("Size"));
                r.add(sld("Scale", "scale", 0.5, 3, 0.05, "x"));
                r.add(sld("Width", "width", 160, 360, 1, "px"));
                r.add(sld("Height", "height", 30, 100, 1, "px"));
                r.add(sld("Padding", "padding", 0, 20, 1, "px"));
                r.add(sld("Item slot size", "slotSize", 16, 64, 1, "px"));
                r.add(new Header("Bid history"));
                r.add(sld("Lines under bar", "historyLines", 0, 6, 1, ""));
            }
            case 2 -> {
                r.add(new Header("Shape"));
                r.add(sld("Corner radius", "cornerRadius", 0, 10, 1, "px"));
                r.add(sld("Border thickness", "borderThickness", 0, 4, 1, "px"));
                r.add(tog("Background gradient", "gradient"));
                r.add(tog("Frosted glass", "frosted"));
                r.add(tog("Drop shadow", "shadow"));
                r.add(sld("Shadow offset", "shadowOffset", 1, 6, 1, "px"));
                r.add(new Header("Accent stripe"));
                r.add(cyc("Stripe side", "stripeStyle", "Left", "Right", "Top", "Bottom", "None"));
                r.add(sld("Stripe thickness", "stripeThickness", 1, 8, 1, "px"));
                r.add(new Header("Timer bar"));
                r.add(cyc("Bar position", "barPosition", "Bottom", "Top", "Hidden"));
                r.add(sld("Bar thickness", "barThickness", 1, 10, 1, "px"));
                r.add(cyc("Bar style", "barStyle", "Solid", "Segmented"));
                r.add(tog("Fill up instead of drain", "barFill"));
            }
            case 3 -> {
                r.add(new Header("Presets"));
                r.add(new ButtonRow(new String[]{"Gold", "Neon", "Ocean", "Blood"},
                        new int[]{0xFFFFAA00, 0xFF00F0FF, 0xFF3FA9F5, 0xFFE63946}, new Runnable[]{
                        preset(0xCC101018, 0xCC252540, 0xFFFFAA00, 0xFFFF4444, 0xFFFFFFFF, 0xFFAAAAAA, 0xFF55FF55, 0xFF2A2A35),
                        preset(0xDD0B0B1E, 0xDD1A0B33, 0xFF00F0FF, 0xFFFF2E88, 0xFFFFFFFF, 0xFF9AA5FF, 0xFF39FF14, 0xFF1A1A3A),
                        preset(0xDD0A1A2A, 0xDD123456, 0xFF3FA9F5, 0xFFFF5252, 0xFFFFFFFF, 0xFF9CC4E4, 0xFF7CFFCB, 0xFF14283C),
                        preset(0xDD1A0808, 0xDD3A0F0F, 0xFFE63946, 0xFFFFD166, 0xFFFFFFFF, 0xFFC9A0A0, 0xFFFFD166, 0xFF2A1010)}));
                r.add(new ButtonRow(new String[]{"Mint", "Violet", "Light", "Mono"},
                        new int[]{0xFF2EE6A6, 0xFFB36BFF, 0xFF4C6EF5, 0xFFFFFFFF}, new Runnable[]{
                        preset(0xDD0E1F1A, 0xDD14382E, 0xFF2EE6A6, 0xFFFF6B6B, 0xFFFFFFFF, 0xFF9CCFBF, 0xFFB8FFE6, 0xFF14302A),
                        preset(0xDD170F26, 0xDD2B1650, 0xFFB36BFF, 0xFFFF5C8A, 0xFFFFFFFF, 0xFFC4A8E8, 0xFFFFD6FF, 0xFF251845),
                        preset(0xEEF4F4F8, 0xEEDDDDE8, 0xFF4C6EF5, 0xFFE03131, 0xFF1B1B25, 0xFF555566, 0xFF2B8A3E, 0xFFCCCCD8),
                        preset(0xDD000000, 0xDD222222, 0xFFFFFFFF, 0xFFFF4444, 0xFFFFFFFF, 0xFF999999, 0xFFFFFFFF, 0xFF333333)}));
                r.add(sld("Master opacity", "opacity", 10, 100, 1, "%"));
                r.add(new Header("Panel"));
                r.add(col("Background", "cBg"));
                r.add(col("Background 2 (gradient)", "cBg2"));
                r.add(col("Border", "cBorder"));
                r.add(col("Shadow", "cShadow"));
                r.add(new Header("Accents"));
                r.add(col("Accent", "cAccent"));
                r.add(col("Urgent (last seconds)", "cUrgent"));
                r.add(col("New bid flash", "cFlash"));
                r.add(new Header("Text"));
                r.add(col("Main text", "cText"));
                r.add(col("Secondary text", "cSub"));
                r.add(col("Bid amount", "cBid"));
                r.add(col("Bidder name", "cBidder"));
                r.add(col("Timer", "cTimer"));
                r.add(col("Sold", "cSold"));
                r.add(new Header("Bar and slot"));
                r.add(col("Bar track", "cTrack"));
                r.add(col("Slot background", "cSlot"));
                r.add(col("Slot border", "cSlotBorder"));
            }
            case 4 -> {
                r.add(new Header("Words"));
                r.add(txt("Title", "title", 20));
                r.add(txt("Ended title", "endedTitle", 20));
                r.add(txt("Sold text", "soldText", 20));
                r.add(txt("No bids text", "noBidText", 20));
                r.add(txt("Bidder prefix", "byText", 10));
                r.add(txt("Currency symbol", "currency", 4));
                r.add(txt("Timer suffix", "timerSuffix", 4));
                r.add(new Header("Formatting"));
                r.add(cyc("Timer format", "timerFormat", "Seconds (42s)", "Minutes (0:42)"));
                r.add(cyc("Amount format", "amountFormat", "Short (1.5M)", "Full (1,500,000)"));
                r.add(cyc("Item name case", "nameCase", "As typed", "UPPERCASE", "lowercase"));
                r.add(sld("Text scale", "textScale", 0.7, 1.6, 0.05, "x"));
                r.add(tog("Text shadow", "textShadow"));
                r.add(tog("Add item count to name", "appendCount"));
            }
            case 5 -> {
                r.add(new Header("Show or hide parts"));
                r.add(tog("Item slot", "showSlot"));
                r.add(tog("Item icon", "showItem"));
                r.add(tog("Item count number", "showCount"));
                r.add(tog("Title", "showTitle"));
                r.add(tog("Timer", "showTimer"));
                r.add(tog("Item name", "showName"));
                r.add(tog("Bid amount", "showBid"));
                r.add(tog("Bidder name", "showBidder"));
                r.add(new Header("Timer placement"));
                r.add(cyc("Timer position", "timerPos", "Top right", "Bid line right", "Big on right"));
            }
            case 6 -> {
                r.add(new Header("New bid"));
                r.add(tog("Flash", "flash"));
                r.add(sld("Flash length", "flashMs", 100, 2000, 50, "ms"));
                r.add(tog("Bid amount pop", "pop"));
                r.add(tog("Shake", "shake"));
                r.add(sld("Shake strength", "shakeAmount", 1, 8, 1, "px"));
                r.add(new Header("Intro"));
                r.add(tog("Slide in on start", "slideIn"));
                r.add(sld("Slide time", "slideMs", 100, 1500, 50, "ms"));
                r.add(new Header("Item icon"));
                r.add(tog("Bobbing", "bob"));
                r.add(sld("Bob speed", "bobSpeed", 0.3, 3, 0.1, "x"));
                r.add(sld("Bob height", "bobHeight", 0.5, 4, 0.1, "px"));
                r.add(new Header("Countdown"));
                r.add(sld("Urgent below", "urgentSeconds", 3, 30, 1, "s"));
                r.add(tog("Blink timer when urgent", "blinkUrgent"));
                r.add(sld("Show result for", "endedSeconds", 1, 30, 1, "s"));
                r.add(tog("Always show bar (for setup)", "alwaysShow"));
            }
            case 7 -> {
                r.add(new Header("Say in public chat"));
                r.add(new Note("Messages are sent to the server chat as you. Off by default."));
                r.add(tog("Announce start", "announceStart"));
                r.add(tog("Announce new top bid", "announceBid"));
                r.add(tog("Announce winner", "announceEnd"));
                r.add(tog("Countdown (30s, 10s, 5s)", "announceCountdown"));
                r.add(new Header("Sounds"));
                r.add(tog("New bid sound", "soundBid"));
                r.add(tog("Sold sound", "soundEnd"));
                r.add(tog("Countdown ticks (last 5s)", "soundCountdown"));
                r.add(sld("Volume", "volume", 0, 100, 5, "%"));
                r.add(new Header("Only you see these"));
                r.add(tog("Bid notifications in chat", "localMessages"));
                r.add(tog("Refund list when auction ends", "showRefunds"));
            }
            case 8 -> {
                r.add(new Header("Payment message"));
                r.add(new Note("Regex with 3 groups: name, number, k/m/b."));
                r.add(txt("Pay regex", "payRegex", 200));
                r.add(tog("Show raw chat lines", "debugChat"));
                r.add(new Header("Test without real money"));
                r.add(new ButtonRow(new String[]{"Steve 100K", "Alex 250K", "Pro 1M"}, null, new Runnable[]{
                        () -> testBid("Steve", 100_000), () -> testBid("Alex", 250_000), () -> testBid("Pro", 1_000_000)}));
                r.add(new Header("Reset"));
                r.add(new ButtonRow(new String[]{"Reset everything"}, null, new Runnable[]{
                        () -> { BidConfig.get().resetAll(); setTab(tab); }}));
            }
            default -> {
                r.add(new Header("Public chat templates"));
                r.add(new Note("Use {item} {bid} {bidder} {time} {me}."));
                r.add(new Note("Enable each announcement in Chat & sound."));
                r.add(txt("Auction start", "msgStart", 100));
                r.add(txt("New top bid", "msgBid", 100));
                r.add(txt("Auction winner", "msgEnd", 100));
                r.add(txt("No bids", "msgNoBids", 100));
                r.add(txt("Countdown", "msgCountdown", 100));
            }
        }
        return r;
    }

    private void testBid(String who, long amount) {
        if (!Auction.active) BidMod.say("\u00a7cStart an auction first.");
        else Auction.onBid(who, amount);
    }

    // ------------------------------------------------------------------ item lists
    private List<ItemStack> inventoryStacks() {
        List<ItemStack> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return out;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (!s.isEmpty()) out.add(s);
        }
        ItemStack off = mc.player.getOffhandItem();
        if (!off.isEmpty()) out.add(off);
        return out;
    }

    private List<ItemStack> favoriteStacks() {
        List<ItemStack> out = new ArrayList<>();
        BidConfig cfg = BidConfig.get();
        if (cfg.favoriteItemIds == null) cfg.favoriteItemIds = new ArrayList<>();
        for (String id : cfg.favoriteItemIds) {
            Identifier itemId = Identifier.tryParse(id);
            if (itemId == null) continue;
            Item item = BuiltInRegistries.ITEM.getValue(itemId);
            if (item != Items.AIR) out.add(new ItemStack(item));
        }
        return out;
    }

    private boolean isFavorite(ItemStack stack) {
        List<String> ids = BidConfig.get().favoriteItemIds;
        return ids != null && ids.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    private void toggleFavorite(ItemStack stack) {
        BidConfig cfg = BidConfig.get();
        if (cfg.favoriteItemIds == null) cfg.favoriteItemIds = new ArrayList<>();
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (!cfg.favoriteItemIds.remove(id)) cfg.favoriteItemIds.add(id);
        cfg.markDirty();
        cfg.saveIfDirty();
    }

    private List<ItemStack> allStacks() {
        if (ALL == null) {
            ALL = new ArrayList<>();
            for (Item it : BuiltInRegistries.ITEM) {
                if (it != Items.AIR) ALL.add(new ItemStack(it));
            }
        }
        if (!search.equals(filteredFor)) {
            filteredFor = search;
            String q = search.toLowerCase(Locale.ROOT).trim();
            filtered = new ArrayList<>();
            for (ItemStack s : ALL) {
                if (q.isEmpty()
                        || s.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)
                        || BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().contains(q.replace(' ', '_'))) {
                    filtered.add(s);
                }
            }
        }
        return filtered;
    }

    private void pickInventory(ItemStack s) {
        BidConfig c = BidConfig.get();
        c.useHeld = false;
        c.itemId = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
        c.itemCount = Math.max(1, Math.min(64, s.getCount()));
        c.markDirty();
        c.saveIfDirty();
    }

    private void pickAny(ItemStack s) {
        BidConfig c = BidConfig.get();
        c.useHeld = false;
        c.itemId = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
        if (c.itemCount < 1) c.itemCount = 1;
        c.markDirty();
        c.saveIfDirty();
    }

    // ------------------------------------------------------------------ screen lifecycle
    @Override
    protected void init() {
        BidConfig cfg = BidConfig.get();
        double sizeFactor = cfg.menuSize == 0 ? 0.58 : cfg.menuSize == 2 ? 0.90 : 0.78;
        double heightFactor = cfg.menuSize == 0 ? 0.72 : cfg.menuSize == 2 ? 0.90 : 0.82;
        int maxWidth = cfg.menuSize == 0 ? 440 : cfg.menuSize == 2 ? 760 : 620;
        int maxHeight = cfg.menuSize == 0 ? 280 : cfg.menuSize == 2 ? 440 : 350;
        pW = Math.min(width - 12, Math.min(maxWidth, Math.max(Math.min(320, width - 12), (int) (width * sizeFactor))));
        pH = Math.min(height - 12, Math.min(maxHeight, Math.max(Math.min(240, height - 12), (int) (height * heightFactor))));
        pX = (width - pW) / 2;
        pY = (height - pH) / 2;
        cX = pX + SIDE_W + 6;
        cW = pW - SIDE_W - 12;
        cY = pY + 28 + STRIP_H + 6;
        cB = pY + pH - 30;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        hover = ItemStack.EMPTY;
        BidConfig cfg = BidConfig.get();
        int acc = acc();

        ctx.fill(0, 0, width, height, 0x70000000);
        ctx.fill(pX - 1, pY - 1, pX + pW + 1, pY + pH + 1, 0xFF2B2B3A);
        ctx.fill(pX, pY, pX + pW, pY + pH, 0xFF12121A);
        ctx.fill(pX, pY, pX + pW, pY + 2, acc);

        ctx.text(font, "BID HUD", pX + 8, pY + 10, acc, true);
        stxt(ctx, BidMod.menuKeyLabel() + " closes", pX + 12 + font.width("BID HUD"), pY + 11, 0xFF777788, 0.85f);

        int resetX = pX + pW - 20 - 62;
        button(ctx, resetX, pY + 7, 56, 14, "Reset tab", in(mx, my, resetX, pY + 7, 56, 14), 0);
        button(ctx, pX + pW - 20, pY + 7, 14, 14, "x", in(mx, my, pX + pW - 20, pY + 7, 14, 14), 0);

        // sidebar
        ctx.fill(pX, pY + 26, pX + SIDE_W, pY + pH, 0xFF0D0D13);
        for (int i = 0; i < TABS.length; i++) {
            int ty = sidebarTabY(i), th = sidebarTabHeight();
            boolean sel = i == tab, hv = in(mx, my, pX + 4, ty, SIDE_W - 8, th);
            if (sel) {
                ctx.fill(pX + 4, ty, pX + SIDE_W - 4, ty + th, 0xFF1C1C28);
                ctx.fill(pX + 4, ty, pX + 6, ty + th, acc);
            } else if (hv) {
                ctx.fill(pX + 4, ty, pX + SIDE_W - 4, ty + th, 0xFF16161F);
            }
            ctx.text(font, TABS[i], pX + 12, ty + (th - 8) / 2, sel ? 0xFFFFFFFF : 0xFF9999AA, false);
        }
        ctx.text(font, "Made by: Mintixx_", pX + 7, pY + pH - 18, 0xFF8F8FA3, false);

        // live preview strip
        int sTop = pY + 28;
        ctx.fill(cX, sTop, cX + cW, sTop + STRIP_H, 0xFF0A0A10);
        for (int gx = cX; gx < cX + cW; gx += 12) ctx.fill(gx, sTop, gx + 1, sTop + STRIP_H, 0x0CFFFFFF);
        for (int gy = sTop; gy < sTop + STRIP_H; gy += 12) ctx.fill(cX, gy, cX + cW, gy + 1, 0x0CFFFFFF);
        float ps = Math.min(1f, Math.min((cW - 16f) / cfg.width, (STRIP_H - 8f) / BidHud.fullH()));
        ps = Math.max(0.25f, ps);
        float bx = cX + (cW - cfg.width * ps) / 2f;
        float by = sTop + (STRIP_H - BidHud.fullH() * ps) / 2f;
        BidHud.draw(ctx, bx, by, ps, BidHud.current());

        // rows
        int total = 0;
        for (int i = 0; i < rows.size(); i++) {
            if (rowVisible(i)) total += rows.get(i).h(this);
        }
        maxScroll = Math.max(0, total - (cB - cY));
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        ctx.enableScissor(cX, cY, cX + cW, cB);
        int y = cY - (int) scroll;
        for (int i = 0; i < rows.size(); i++) {
            if (!rowVisible(i)) continue;
            Row r = rows.get(i);
            int h = r.h(this);
            if (y + h > cY && y < cB) r.render(this, ctx, cX, y, cW - 10, mx, my);
            y += h;
        }
        ctx.disableScissor();
        if (maxScroll > 0) {
            int barH = thumbHeight();
            int barY = thumbY();
            ctx.fill(cX + cW - 7, cY, cX + cW, cB, 0xFF1A1A24);
            ctx.fill(cX + cW - 7, barY, cX + cW, barY + barH, acc);
        }

        // toolbar
        String[] tb = {Auction.active ? "Restart auction" : "Start auction", "End now", "Cancel", "Drag position"};
        int bw = (cW - 12) / 4;
        for (int i = 0; i < 4; i++) {
            int bxx = cX + i * (bw + 4);
            int byy = pY + pH - 26;
            boolean h = in(mx, my, bxx, byy, bw, 20);
            button(ctx, bxx, byy, bw, 20, tb[i], h, i == 0 ? acc : 0);
        }

        if (!hover.isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            Item.TooltipContext tooltipContext = mc.level == null
                    ? Item.TooltipContext.EMPTY : Item.TooltipContext.of(mc.level);
            List<Component> tooltip = hover.getTooltipLines(tooltipContext, mc.player, net.minecraft.world.item.TooltipFlag.NORMAL);
            ctx.setTooltipForNextFrame(font, tooltip, hover.getTooltipImage(), mx, my);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int btn = event.button();
        double dmx = event.x(), dmy = event.y();
        int mx = (int) dmx, my = (int) dmy;
        clearTextFocus();
        if (!isClickButton(btn)) return super.mouseClicked(event, doubleClick);
        if (in(mx, my, pX + pW - 20, pY + 7, 14, 14)) { onClose(); return true; }
        int resetX = pX + pW - 20 - 62;
        if (in(mx, my, resetX, pY + 7, 56, 14)) {
            BidConfig.get().reset(curFields.toArray(new String[0]));
            return true;
        }
        for (int i = 0; i < TABS.length; i++) {
            if (in(mx, my, pX + 4, sidebarTabY(i), SIDE_W - 8, sidebarTabHeight())) {
                setTab(i);
                return true;
            }
        }
        int bw = (cW - 12) / 4;
        for (int i = 0; i < 4; i++) {
            if (in(mx, my, cX + i * (bw + 4), pY + pH - 26, bw, 20)) {
                switch (i) {
                    case 0 -> Auction.start();
                    case 1 -> Auction.finish(false);
                    case 2 -> Auction.finish(true);
                    default -> Minecraft.getInstance().setScreenAndShow(new HudEditorScreen(this));
                }
                return true;
            }
        }
        if (btn == 0 && maxScroll > 0 && in(mx, my, cX + cW - 8, cY, 8, cB - cY)) {
            int thumbTop = thumbY();
            int thumbH = thumbHeight();
            if (my >= thumbTop && my < thumbTop + thumbH) {
                scrollGrabOffset = my - thumbTop;
            } else {
                scrollGrabOffset = thumbH / 2;
                dragScrollThumb(my);
            }
            draggingScroll = true;
            return true;
        }
        if (in(mx, my, cX, cY, cW, cB - cY)) {
            int y = cY - (int) scroll;
            for (int i = 0; i < rows.size(); i++) {
                if (!rowVisible(i)) continue;
                Row r = rows.get(i);
                int h = r.h(this);
                if (my >= y && my < y + h) {
                    if (r.click(this, cX, y, cW - 10, mx, my, btn)) {
                        active = r; ax = cX; ay = y; aw = cW - 10;
                    }
                    return true;
                }
                y += h;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        double mx = event.x(), my = event.y();
        if (draggingScroll) {
            dragScrollThumb((int) my);
            return true;
        }
        if (active != null) {
            active.drag(this, ax, ay, aw, (int) mx, (int) my);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingScroll = false;
        if (active != null) active.release();
        active = null;
        BidConfig.get().saveIfDirty();
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        scroll = Math.max(0, Math.min(maxScroll, scroll - v * 24));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (BidMod.menuKeyPressed(event)) {
            onClose();
            return true;
        }
        if (focus != null) {
            if (event.isEscape() || event.isConfirmation()) {
                clearTextFocus();
                return true;
            }
            focus.input.keyPressed(event);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (focus != null) {
            focus.input.charTyped(event);
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public void onClose() { Minecraft.getInstance().setScreenAndShow(parent); }

    @Override
    public void removed() { BidConfig.get().save(); }

    @Override
    public boolean isPauseScreen() { return false; }

    // ------------------------------------------------------------------ config field binding
    static final class F {
        final Field f;

        F(String n) {
            try { f = BidConfig.class.getField(n); }
            catch (Exception e) { throw new RuntimeException(e); }
        }

        Object get() {
            try { return f.get(BidConfig.get()); } catch (Exception e) { return null; }
        }

        void set(Object v) {
            try { f.set(BidConfig.get(), v); BidConfig.get().markDirty(); } catch (Exception ignored) {}
        }

        boolean b() { return (Boolean) get(); }
        int i() { return (Integer) get(); }
        float fl() { return (Float) get(); }
        String s() { return (String) get(); }
    }

    // ------------------------------------------------------------------ rows
    abstract static class Row {
        abstract int h(BidMenuScreen s);
        abstract void render(BidMenuScreen s, GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my);
        boolean click(BidMenuScreen s, int x, int y, int w, int mx, int my, int btn) { return false; }
        void drag(BidMenuScreen s, int x, int y, int w, int mx, int my) {}
        void release() {}
    }

    static final class Header extends Row {
        final String label;
        Header(String l) { label = l; }
        int h(BidMenuScreen s) { return 20; }
        void render(BidMenuScreen s, GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my) {
            boolean collapsed = s.collapsedSections.contains(s.sectionKey(this));
            ctx.text(s.tr(), collapsed ? "\u25b6" : "\u25bc", x + 2, y + 6, s.acc(), false);
            ctx.text(s.tr(), label.toUpperCase(Locale.ROOT), x + 12, y + 9, s.acc(), false);
            ctx.fill(x + 2, y + 18, x + w, y + 19, 0xFF262634);
        }
        boolean click(BidMenuScreen s, int x, int y, int w, int mx, int my, int btn) {
            if (isClickButton(btn)) s.toggleSection(this);
            return false;
        }
    }

    static final class Note extends Row {
        final String text;
        Note(String t) { text = t; }
        int h(BidMenuScreen s) { return 14; }
        void render(BidMenuScreen s, GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my) {
            s.stxt(ctx, s.tr().plainSubstrByWidth(text, (int) (w / 0.85f)), x + 4, y + 3, 0xFF7C7C90, 0.85f);
        }
    }

    static final class Toggle extends Row {
        final String label;
        final Supplier<Boolean> g;
        final Consumer<Boolean> st;
        Toggle(String l, Supplier<Boolean> g, Consumer<Boolean> st) { label = l; this.g = g; this.st = st; }
        int h(BidMenuScreen s) { return 20; }
        void render(BidMenuScreen s, GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my) {
            if (s.hov(x, y, w, 20, mx, my)) ctx.fill(x, y, x + w, y + 20, 0xFF181822);
            ctx.text(s.tr(), label, x + 4, y + 6, 0xFFDDDDE6, false);
            boolean on = g.get();
            int sx = x + w - 30, sy = y + 4;
            ctx.fill(sx, sy, sx + 26, sy + 12, on ? s.acc() : 0xFF3A3A48);
            ctx.fill(on ? sx + 16 : sx + 2, sy + 2, on ? sx + 24 : sx + 10, sy + 10, 0xFFFFFFFF);
        }
        boolean click(BidMenuScreen s, int x, int y, int w, int mx, int my, int btn) {
            if (!isClickButton(btn)) return false;
            st.accept(!g.get());
            return false;
        }
    }

    static final class Slider extends Row {
        final String label;
        final DoubleSupplier g;
        final DoubleConsumer st;
        final double min, max, step;
        final DoubleFunction<String> fmt;
        Slider(String l, DoubleSupplier g, DoubleConsumer st, double min, double max, double step, DoubleFunction<String> fmt) {
            label = l; this.g = g; this.st = st; this.min = min; this.max = max; this.step = step; this.fmt = fmt;
        }
        int h(BidMenuScreen s) { return 20; }
        int tx(int x, int w) { return x + (int) (w * 0.42); }
        int tw(int x, int w) { return x + w - 58 - tx(x, w); }
        void render(BidMenuScreen s, GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my) {
            if (s.hov(x, y, w, 20, mx, my)) ctx.fill(x, y, x + w, y + 20, 0xFF181822);
            ctx.text(s.tr(), label, x + 4, y + 6, 0xFFDDDDE6, false);
            int tx = tx(x, w), tw = tw(x, w);
            double t = Math.max(0, Math.min(1, (g.getAsDouble() - min) / (max - min)));
            ctx.fill(tx, y + 8, tx + tw, y + 12, 0xFF262633);
            ctx.fill(tx, y + 8, tx + (int) (tw * t), y + 12, s.acc());
            int kx = tx + (int) (tw * t);
            ctx.fill(kx - 2, y + 4, kx + 2, y + 16, 0xFFFFFFFF);
            String val = fmt.apply(g.getAsDouble());
            ctx.text(s.tr(), val, x + w - 4 - s.tr().width(val), y + 6, 0xFFFFFFFF, false);
        }
        void setFrom(int x, int w, int mx) {
            int tx = tx(x, w), tw = tw(x, w);
            double t = Math.max(0, Math.min(1, (mx - tx) / (double) tw));
            double v = min + t * (max - min);
            v = Math.round(v / step) * step;
            st.accept(Math.max(min, Math.min(max, v)));
        }
        boolean click(BidMenuScreen s, int x, int y, int w, int mx, int my, int btn) {
            if (!isClickButton(btn) || mx < tx(x, w) - 4 || mx > tx(x, w) + tw(x, w) + 4) return false;
            setFrom(x, w, mx);
            return true;
        }
        void drag(BidMenuScreen s, int x, int y, int w, int mx, int my) { setFrom(x, w, mx); }
    }

    static final class Cycle extends Row {
        final String label;
        final String[] opts;
        final IntSupplier g;
        final IntConsumer st;
        Cycle(String l, String[] o, IntSupplier g, IntConsumer st) { label = l; opts = o; this.g = g; this.st = st; }
        int h(BidMenuScreen s) { return 22; }
        void render(BidMenuScreen s, GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my) {
            if (s.hov(x, y, w, 22, mx, my)) ctx.fill(x, y, x + w, y + 22, 0xFF181822);
            ctx.text(s.tr(), label, x + 4, y + 7, 0xFFDDDDE6, false);
            int bx = x + (int) (w * 0.42), bw = w - (int) (w * 0.42) - 4;
            boolean hv = s.hov(bx, y + 2, bw, 18, mx, my);
            ctx.fill(bx, y + 2, bx + bw, y + 20, hv ? 0xFF262636 : 0xFF1A1A24);
            int i = Math.max(0, Math.min(opts.length - 1, g.getAsInt()));
            String v = opts[i];
            ctx.text(s.tr(), v, bx + (bw - s.tr().width(v)) / 2, y + 7, 0xFFFFFFFF, false);
            ctx.text(s.tr(), "<", bx + 5, y + 7, 0xFF8888A0, false);
            ctx.text(s.tr(), ">", bx + bw - 11, y + 7, 0xFF8888A0, false);
        }
        boolean click(BidMenuScreen s, int x, int y, int w, int mx, int my, int btn) {
            int bx = x + (int) (w * 0.42);
            if (mx < bx) return false;
            int i = g.getAsInt();
            if (btn == 0) i = (i + 1) % opts.length;
            else if (btn == 1) i = (i + opts.length - 1) % opts.length;
            else return false;
            st.accept(i);
            s.init();
            return false;
        }
    }

    static final class ButtonRow extends Row {
        final String[] labels;
        final int[] chips;
        final Runnable[] acts;
        ButtonRow(String[] l, int[] c, Runnable[] a) { labels = l; chips = c; acts = a; }
        int h(BidMenuScreen s) { return 24; }
        int bw(int w) { return (w - (labels.length - 1) * 4) / labels.length; }
        void render(BidMenuScreen s, GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my) {
            int bw = bw(w);
            for (int i = 0; i < labels.length; i++) {
                int bx = x + i * (bw + 4);
                s.button(ctx, bx, y + 2, bw, 20, labels[i], s.hov(bx, y + 2, bw, 20, mx, my), chips == null ? 0 : chips[i]);
            }
        }
        boolean click(BidMenuScreen s, int x, int y, int w, int mx, int my, int btn) {
            if (!isClickButton(btn)) return false;
            int bw = bw(w);
            for (int i = 0; i < labels.length; i++) {
                if (in(mx, my, x + i * (bw + 4), y + 2, bw, 20)) { acts[i].run(); break; }
            }
            return false;
        }
    }

    static final class ColorRow extends Row {
        static final int[] TINT = {0xFFE05555, 0xFF55C46A, 0xFF5588F0, 0xFFBBBBBB};
        static final String[] CH = {"R", "G", "B", "A"};
        final String label;
        final F f;
        int chan = -1;
        ColorRow(String l, F f) { label = l; this.f = f; }
        int h(BidMenuScreen s) { return 36; }
        int seg(int w) { return (w - 8 - 9) / 4; }
        int sx(int x, int w, int i) { return x + 4 + i * (seg(w) + 3); }
        static int ch(int c, int i) { return i == 3 ? c >>> 24 : (c >> (16 - 8 * i)) & 255; }
        static int with(int c, int i, int v) {
            int a = c >>> 24, r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
            if (i == 0) r = v; else if (i == 1) g = v; else if (i == 2) b = v; else a = v;
            return (a << 24) | (r << 16) | (g << 8) | b;
        }
        void render(BidMenuScreen s, GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my) {
            int c = f.i();
            ctx.text(s.tr(), label, x + 4, y + 3, 0xFFDDDDE6, false);
            int swx = x + w - 30;
            ctx.fill(swx, y + 2, swx + 26, y + 12, 0xFF888890);
            ctx.fill(swx, y + 2, swx + 26, y + 12, c);
            String hex = String.format("#%06X", c & 0xFFFFFF);
            ctx.text(s.tr(), hex, swx - 6 - s.tr().width(hex), y + 3, 0xFF8C8CA0, false);
            for (int i = 0; i < 4; i++) {
                int bx = sx(x, w, i), by = y + 17, sw = seg(w);
                ctx.fill(bx, by, bx + sw, by + 14, 0xFF1E1E28);
                ctx.fill(bx, by, bx + sw * ch(c, i) / 255, by + 14, TINT[i]);
                s.stxt(ctx, CH[i] + " " + ch(c, i), bx + 3, by + 4, 0xFFFFFFFF, 0.8f);
            }
        }
        void setFrom(int x, int w, int mx) {
            int sw = seg(w);
            int v = (int) Math.max(0, Math.min(255, (mx - sx(x, w, chan)) * 255.0 / sw));
            f.set(with(f.i(), chan, v));
        }
        boolean click(BidMenuScreen s, int x, int y, int w, int mx, int my, int btn) {
            if (!isClickButton(btn) || my < y + 15 || my > y + 33) return false;
            for (int i = 0; i < 4; i++) {
                if (mx >= sx(x, w, i) && mx < sx(x, w, i) + seg(w)) {
                    chan = i;
                    setFrom(x, w, mx);
                    return true;
                }
            }
            return false;
        }
        void drag(BidMenuScreen s, int x, int y, int w, int mx, int my) { if (chan >= 0) setFrom(x, w, mx); }
        void release() { chan = -1; }
    }

    static final class TextRow extends Row {
        final String label;
        final Supplier<String> g;
        final Consumer<String> st;
        final int max;
        EditBox input;
        TextRow(String l, Supplier<String> g, Consumer<String> st, int max) { label = l; this.g = g; this.st = st; this.max = max; }
        int h(BidMenuScreen s) { return 24; }
        int bx(int x, int w) { return x + (int) (w * 0.38); }
        void render(BidMenuScreen s, GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my) {
            ctx.text(s.tr(), label, x + 4, y + 8, 0xFFDDDDE6, false);
            int bx = bx(x, w), bw = x + w - 4 - bx;
            boolean f = s.focus == this;
            ctx.fill(bx, y + 3, bx + bw, y + 21, 0xFF0B0B10);
            ctx.outline(bx, y + 3, bw, 18, f ? s.acc() : 0xFF2C2C3A);
            String t = g.get();
            if (t.isEmpty() && !f) {
                ctx.text(s.tr(), "empty", bx + 5, y + 8, 0xFF55556A, false);
            } else {
                String shown = s.tr().plainSubstrByWidth(t, bw - 10, true);
                ctx.text(s.tr(), shown, bx + 5, y + 8, 0xFFFFFFFF, false);
                if (f && (System.currentTimeMillis() / 500) % 2 == 0) {
                    int cx = bx + 5 + s.tr().width(shown);
                    ctx.fill(cx, y + 6, cx + 1, y + 18, 0xFFFFFFFF);
                }
            }
        }
        boolean click(BidMenuScreen s, int x, int y, int w, int mx, int my, int btn) {
            if (isClickButton(btn) && my >= y + 3 && my < y + 21
                    && mx >= bx(x, w) && mx < x + w - 4) {
                s.focus(this);
                return false;
            }
            return false;
        }
    }

    static final class ItemGridRow extends Row {
        static final int CELL = 20;
        final Supplier<List<ItemStack>> items;
        final Consumer<ItemStack> pick;
        final boolean showFavorites;
        ItemGridRow(Supplier<List<ItemStack>> i, Consumer<ItemStack> p, boolean showFavorites) {
            items = i;
            pick = p;
            this.showFavorites = showFavorites;
        }
        int cols(int w) { return Math.max(1, (w - 8) / CELL); }
        int h(BidMenuScreen s) {
            int n = items.get().size();
            if (n == 0) return 24;
            int cols = cols(s.cW - 8);
            return ((n + cols - 1) / cols) * CELL + 6;
        }
        void render(BidMenuScreen s, GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my) {
            List<ItemStack> list = items.get();
            if (list.isEmpty()) {
                ctx.text(s.tr(), "Nothing here.", x + 6, y + 8, 0xFF7C7C90, false);
                return;
            }
            BidConfig c = BidConfig.get();
            Identifier chosenId = Identifier.tryParse(c.itemId);
            Item chosen = (!c.useHeld && chosenId != null) ? BuiltInRegistries.ITEM.getValue(chosenId) : null;
            int cols = cols(w);
            int first = Math.max(0, (s.cY - y - 3) / CELL);
            for (int i = first * cols; i < list.size(); i++) {
                int cx = x + 4 + (i % cols) * CELL;
                int cy = y + 3 + (i / cols) * CELL;
                if (cy > s.cB) break;
                if (cy + CELL < s.cY) continue;
                ItemStack st = list.get(i);
                boolean hv = s.hov(cx, cy, CELL - 1, CELL - 1, mx, my);
                boolean sel = chosen != null && st.getItem() == chosen;
                ctx.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, hv ? 0xFF2C2C3E : 0xFF1A1A24);
                if (sel) ctx.outline(cx, cy, CELL - 1, CELL - 1, s.acc());
                ctx.item(st, cx + 2, cy + 2);
                if (st.getCount() > 1) ctx.itemDecorations(s.tr(), st, cx + 2, cy + 2);
                if (showFavorites && (hv || s.isFavorite(st))) {
                    boolean favorite = s.isFavorite(st);
                    ctx.text(s.tr(), favorite ? "\u2665" : "\u2661", cx + CELL - 8, cy,
                            favorite ? 0xFFFF6688 : 0xFFBBBBCC, false);
                }
                if (hv) s.hover = st;
            }
        }
        boolean click(BidMenuScreen s, int x, int y, int w, int mx, int my, int btn) {
            if (!isClickButton(btn)) return false;
            List<ItemStack> list = items.get();
            int cols = cols(w);
            int col = (mx - x - 4) / CELL, row = (my - y - 3) / CELL;
            if (mx < x + 4 || my < y + 3 || col >= cols) return false;
            int i = row * cols + col;
            if (i >= 0 && i < list.size()) {
                ItemStack stack = list.get(i);
                int cellX = x + 4 + col * CELL;
                int cellY = y + 3 + row * CELL;
                if (showFavorites && mx >= cellX + CELL - 9 && my < cellY + 9) {
                    s.toggleFavorite(stack);
                } else {
                    pick.accept(stack);
                }
            }
            return false;
        }
    }
}
