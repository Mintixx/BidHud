package com.bidhud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class BidConfig {
    // ---------- layout ----------
    public int anchor = 0;                 // 0 TL,1 TC,2 TR,3 ML,4 C,5 MR,6 BL,7 BC,8 BR
    public int offsetX = 10, offsetY = 10;
    public float scale = 1f;
    public int width = 190, height = 46, padding = 8, slotSize = 32, historyLines = 0;
    public int menuSize = 1;               // 0 compact, 1 regular, 2 large

    // ---------- style ----------
    public int cornerRadius = 0, borderThickness = 0;
    public int stripeStyle = 0;            // 0 left,1 right,2 top,3 bottom,4 none
    public int stripeThickness = 3;
    public boolean gradient = false, shadow = false, frosted = false;
    public int shadowOffset = 2;
    public int barPosition = 0;            // 0 bottom,1 top,2 hidden
    public int barThickness = 4;
    public int barStyle = 0;               // 0 solid,1 segmented
    public boolean barFill = false;

    // ---------- colors (ARGB) ----------
    public int opacity = 100;
    public int cBg = 0xCC101018, cBg2 = 0xCC252540, cAccent = 0xFFFFAA00, cUrgent = 0xFFFF4444;
    public int cText = 0xFFFFFFFF, cSub = 0xFFAAAAAA, cBid = 0xFF55FF55, cBidder = 0xFFAAAAAA;
    public int cTimer = 0xFFFFFFFF, cSold = 0xFF55FF55, cTrack = 0xFF2A2A35;
    public int cSlot = 0x66000000, cSlotBorder = 0x44FFFFFF, cBorder = 0xFFFFAA00;
    public int cShadow = 0x88000000, cFlash = 0xFFFFFFFF;

    // ---------- text ----------
    public String title = "AUCTION", endedTitle = "ENDED", soldText = "SOLD", noBidText = "No bids";
    public String byText = "by ", currency = "$", timerSuffix = "s";
    public int timerFormat = 0;            // 0 seconds, 1 m:ss
    public int amountFormat = 0;           // 0 short, 1 full
    public int nameCase = 0;               // 0 as typed, 1 UPPER, 2 lower
    public float textScale = 1f;
    public boolean textShadow = true, appendCount = true;

    // ---------- elements ----------
    public boolean showSlot = true, showItem = true, showCount = true, showTitle = true;
    public boolean showTimer = true, showName = true, showBid = true, showBidder = true;
    public int timerPos = 0;               // 0 top right, 1 bid line right, 2 big on right

    // ---------- effects ----------
    public boolean flash = true, pop = true, shake = false, slideIn = true, bob = false, blinkUrgent = true;
    public int flashMs = 600, shakeAmount = 2, slideMs = 350, urgentSeconds = 10, endedSeconds = 8;
    public float bobSpeed = 1f, bobHeight = 1f;
    public boolean alwaysShow = false;

    // ---------- auction ----------
    public int duration = 60;
    public int bidTimeExtensionSeconds = 5;
    public boolean streamDelay = false;
    public int streamDelaySeconds = 10;
    public boolean refundPay = true;
    public float refundIntervalSeconds = 1.5f;
    public boolean antiSnipe = false;
    public int antiSnipeSeconds = 5;
    public boolean useHeld = true;
    public String itemId = "";
    public int itemCount = 1;
    public String customName = "";
    public List<String> favoriteItemIds = new ArrayList<>();
    public String startBid = "0", minIncrement = "1k", ignoreList = "";

    // ---------- chat & sound ----------
    public boolean announceStart = false, announceBid = false, announceEnd = false, announceCountdown = false;
    public String msgStart = "AUCTION: {item}! Pay {me} to bid. {time}s left!";
    public String msgBid = "New top bid: {bid} by {bidder}";
    public String msgEnd = "SOLD {item} to {bidder} for {bid}!";
    public String msgNoBids = "Auction over, no bids.";
    public String msgCountdown = "{time}s left! Top bid: {bid}";
    public boolean soundBid = true, soundEnd = true, soundCountdown = false;
    public int volume = 100;
    public boolean localMessages = true, showRefunds = true;

    // ---------- advanced ----------
    public static final String DEFAULT_REGEX = "(\\w{3,16}) paid you \\$?(\\d[\\d.,]*)\\s*([kKmMbB]?)";
    private static final Pattern[] DEFAULT_PAYMENT_PATTERNS = {
            Pattern.compile("(?i)\\b([\\w]{3,16})\\s+(?:has\\s+)?(?:paid|sent|transferred)\\s+you\\s+\\$?([\\d][\\d,]*(?:\\.\\d+)?)\\s*([kmb]?)\\b"),
            Pattern.compile("(?i)\\byou\\s+(?:have\\s+)?(?:received|got)\\s+\\$?([\\d][\\d,]*(?:\\.\\d+)?)\\s*([kmb]?)\\s+(?:from|by)\\s+([\\w]{3,16})\\b"),
            Pattern.compile("(?i)\\byou\\s+(?:have\\s+been\\s+)?paid\\s+\\$?([\\d][\\d,]*(?:\\.\\d+)?)\\s*([kmb]?)\\s+by\\s+([\\w]{3,16})\\b")
    };
    public String payRegex = DEFAULT_REGEX;
    public boolean debugChat = false;

    public record Payment(String player, long amount) {}

    // ---------- plumbing ----------
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("bidhud.json");
    private static BidConfig inst;
    private transient Pattern compiled;
    private transient String compiledSrc;
    private transient boolean dirty;

    public static BidConfig get() {
        if (inst == null) load();
        return inst;
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) inst = GSON.fromJson(Files.readString(FILE), BidConfig.class);
        } catch (Exception ignored) {}
        if (inst == null) inst = new BidConfig();
        if (inst.minIncrement == null || Auction.parse(inst.minIncrement) < 0) {
            inst.minIncrement = "1k";
        }
        inst.save();
    }

    public void save() {
        try { Files.writeString(FILE, GSON.toJson(this)); } catch (Exception ignored) {}
    }

    public void markDirty() { dirty = true; }

    public void saveIfDirty() {
        if (dirty) { dirty = false; save(); }
    }

    public void reset(String... names) {
        BidConfig d = new BidConfig();
        for (String n : names) {
            try {
                Field f = BidConfig.class.getField(n);
                f.set(this, f.get(d));
            } catch (Exception ignored) {}
        }
        save();
    }

    public void resetAll() {
        BidConfig d = new BidConfig();
        for (Field f : BidConfig.class.getFields()) {
            if (Modifier.isStatic(f.getModifiers()) || Modifier.isTransient(f.getModifiers())) continue;
            try { f.set(this, f.get(d)); } catch (Exception ignored) {}
        }
        save();
    }

    public Pattern pattern() {
        if (compiled == null || !payRegex.equals(compiledSrc)) {
            try { compiled = Pattern.compile(payRegex); }
            catch (Exception e) { compiled = Pattern.compile(DEFAULT_REGEX); }
            compiledSrc = payRegex;
        }
        return compiled;
    }

    public Payment parsePayment(String line) {
        var matcher = pattern().matcher(line);
        if (matcher.find() && matcher.groupCount() >= 2) {
            String player = matcher.group(1);
            String amount = matcher.group(2);
            String suffix = matcher.groupCount() >= 3 ? matcher.group(3) : "";
            Payment payment = payment(player, amount, suffix);
            if (payment != null) return payment;
        }

        for (int i = 0; i < DEFAULT_PAYMENT_PATTERNS.length; i++) {
            matcher = DEFAULT_PAYMENT_PATTERNS[i].matcher(line);
            if (!matcher.find()) continue;
            boolean reverse = i != 0;
            String player = matcher.group(reverse ? 3 : 1);
            String amount = matcher.group(reverse ? 1 : 2);
            String suffix = matcher.group(reverse ? 2 : 3);
            Payment payment = payment(player, amount, suffix);
            if (payment != null) return payment;
        }

        return parsePaidYou(line);
    }

    private static Payment parsePaidYou(String line) {
        var paid = Pattern.compile("(?i)\\b([\\w]{3,16})\\s+(?:has\\s+)?paid\\s+you\\b").matcher(line);
        if (!paid.find()) return null;
        String player = paid.group(1);
        var amount = Pattern.compile("(?i)(?<![\\w.])(\\d[\\d,]*(?:\\.\\d+)?)\\s*([kmb]?)\\b")
                .matcher(line.substring(paid.end()));
        if (!amount.find()) return null;
        return payment(player, amount.group(1), amount.group(2));
    }

    private static Payment payment(String player, String amount, String suffix) {
        if (player == null || amount == null) return null;
        long value = Auction.parse(amount + (suffix == null ? "" : suffix));
        return value > 0 ? new Payment(player, value) : null;
    }
}
