package com.bidhud;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;

public class Auction {
    public record Bid(String player, long amount) {}
    private record LatePayment(String player, long amount) {}

    public static boolean active = false;
    private static boolean closing;
    private static long closingEndTime;
    public static String itemName = "";
    public static ItemStack stack = ItemStack.EMPTY;
    public static long topBid = 0;
    public static String topBidder = null;
    public static long startedAt = 0, endTime = 0, durationMs = 60000, finishedAt = 0, lastBidAt = 0;
    public static final Map<String, Long> paid = new LinkedHashMap<>();
    public static final LinkedList<Bid> history = new LinkedList<>();
    private static final Map<String, LatePayment> latePayments = new LinkedHashMap<>();
    private static final LinkedList<LatePayment> pendingRefunds = new LinkedList<>();
    private static long nextRefundAt;
    private static int lastSec = -1;

    // ---------- item selection ----------
    public static ItemStack selectedStack() {
        BidConfig c = BidConfig.get();
        Minecraft mc = Minecraft.getInstance();
        if (c.useHeld) {
            if (mc.player == null) return ItemStack.EMPTY;
            ItemStack h = mc.player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND);
            return h.isEmpty() ? ItemStack.EMPTY : h.copy();
        }
        Identifier id = Identifier.tryParse(c.itemId);
        if (id != null) {
            Item it = BuiltInRegistries.ITEM.getValue(id);
            if (it != Items.AIR) return new ItemStack(it, Math.max(1, c.itemCount));
        }
        return ItemStack.EMPTY;
    }

    public static String nameFor(ItemStack s) {
        BidConfig c = BidConfig.get();
        if (!c.customName.isBlank()) return c.customName;
        if (s.isEmpty()) return "Mystery item";
        String n = s.getHoverName().getString();
        if (c.appendCount && s.getCount() > 1) n += " x" + s.getCount();
        return n;
    }

    // ---------- lifecycle ----------
    public static void start() {
        ItemStack s = selectedStack();
        start(nameFor(s), s);
    }

    public static void start(String name, ItemStack s) {
        BidConfig c = BidConfig.get();
        active = true;
        itemName = name;
        stack = s.copy();
        topBid = 0;
        topBidder = null;
        paid.clear();
        history.clear();
        latePayments.clear();
        closing = false;
        closingEndTime = 0;
        finishedAt = 0;
        lastBidAt = 0;
        lastSec = -1;
        startedAt = System.currentTimeMillis();
        durationMs = c.duration * 1000L;
        endTime = startedAt + durationMs;
        BidMod.say("Auction started: \u00a7e" + name + " \u00a7f(" + c.duration + "s)");
        if (c.announceStart) BidMod.chat(fill(c.msgStart));
        BidMod.play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.3f, c.soundBid);
    }

    public static long remainingMs() {
        long end = closing ? closingEndTime : endTime;
        return Math.max(0, end - System.currentTimeMillis());
    }

    public static boolean isClosing() {
        return closing;
    }

    private static void beginClosing(long now) {
        closing = true;
        int delaySeconds = Math.max(1, Math.min(60, BidConfig.get().streamDelaySeconds));
        closingEndTime = now + delaySeconds * 1000L;
        lastSec = -1;
        BidMod.say("\u00a7eBidding closed. \u00a7fAuction ends in " + delaySeconds + " seconds.");
        if (BidConfig.get().refundPay) {
            BidMod.say("\u00a77Late payments will be refunded after the auction ends.");
        }
    }

    public static void tick() {
        long now = System.currentTimeMillis();
        processPendingRefunds(now);
        if (!active) return;
        BidConfig c = BidConfig.get();
        if (!closing && now >= endTime) {
            if (c.streamDelay) beginClosing(endTime);
            else { finish(false); return; }
        }
        if (closing && (!c.streamDelay || now >= closingEndTime)) {
            finish(false);
            return;
        }
        long rem = remainingMs();
        int sec = (int) Math.ceil(rem / 1000.0);
        if (sec != lastSec) {
            lastSec = sec;
            if (!closing && sec != c.duration) {
                if (c.announceCountdown && (sec == 30 || sec == 10 || sec == 5)) BidMod.chat(fill(c.msgCountdown));
            }
            if (sec <= 5) BidMod.play(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f + 0.1f * (5 - sec), c.soundCountdown);
        }
    }

    private static boolean ignored(String player) {
        for (String s : BidConfig.get().ignoreList.split(",")) {
            if (!s.isBlank() && s.trim().equalsIgnoreCase(player)) return true;
        }
        return false;
    }

    public static long minimumBid() {
        BidConfig c = BidConfig.get();
        long increment = Math.max(1, parse0(c.minIncrement));
        if (topBid == 0) return Math.max(1, parse0(c.startBid));
        return topBid > Long.MAX_VALUE - increment ? Long.MAX_VALUE : topBid + increment;
    }

    public static void onBid(String player, long amount) {
        if (!active) return;
        if (!closing && System.currentTimeMillis() >= endTime) tick();
        if (!active) return;
        if (closing) {
            queueLatePayment(player, amount);
            return;
        }
        if (ignored(player) || amount <= 0) return;
        BidConfig c = BidConfig.get();
        paid.merge(player, amount, Long::sum);
        long requiredBid = minimumBid();
        if (amount < requiredBid) {
            BidMod.info("\u00a77" + player + " paid " + amountText(amount) + "; this payment must be at least "
                    + c.currency + amountText(requiredBid) + " to beat the current bid.");
            return;
        }

        topBid = amount;
        topBidder = player;
        lastBidAt = System.currentTimeMillis();
        history.addFirst(new Bid(player, amount));
        while (history.size() > 20) history.removeLast();
        if (c.bidTimeExtensionSeconds > 0) {
            endTime += c.bidTimeExtensionSeconds * 1000L;
        }
        if (c.antiSnipe && remainingMs() < c.antiSnipeSeconds * 1000L) {
            endTime = Math.max(endTime, System.currentTimeMillis() + c.antiSnipeSeconds * 1000L);
        }
        BidMod.info("\u00a7e" + player + " \u00a7fbid \u00a7a" + c.currency + amountText(amount) + " \u00a7f(new top)");
        BidMod.play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, c.soundBid);
        if (c.announceBid) BidMod.chat(fill(c.msgBid));
    }

    private static void queueLatePayment(String player, long amount) {
        if (!BidConfig.get().refundPay || amount <= 0) return;
        String key = player.toLowerCase(Locale.ROOT);
        latePayments.merge(key, new LatePayment(player, amount),
                (existing, incoming) -> new LatePayment(existing.player(),
                        existing.amount() > Long.MAX_VALUE - incoming.amount()
                                ? Long.MAX_VALUE : existing.amount() + incoming.amount()));
    }

    private static void queueLatePaymentRefunds() {
        for (LatePayment payment : latePayments.values()) {
            if (payment.player() == null || !payment.player().matches("[A-Za-z0-9_]{1,16}")) {
                BidMod.say("\u00a7cCould not safely refund a late payment of " + payment.amount()
                        + "; refund it manually to " + payment.player() + ".");
                continue;
            }
            pendingRefunds.addLast(payment);
        }
        latePayments.clear();
        processPendingRefunds(System.currentTimeMillis());
    }

    private static void processPendingRefunds(long now) {
        if (pendingRefunds.isEmpty() || now < nextRefundAt) return;
        LatePayment payment = pendingRefunds.removeFirst();
        BidMod.command("pay " + payment.player() + " " + payment.amount());
        BidMod.say("\u00a7eLate payment refund: \u00a7f" + payment.player() + " \u00a7a"
                + payment.amount() + "\u00a7f.");
        float intervalSeconds = Math.max(0.5f, Math.min(10f, BidConfig.get().refundIntervalSeconds));
        nextRefundAt = now + Math.round(intervalSeconds * 1000);
    }

    public static void finish(boolean cancelled) {
        if (!active) return;
        BidConfig c = BidConfig.get();
        active = false;
        closing = false;
        closingEndTime = 0;
        finishedAt = System.currentTimeMillis();
        if (cancelled) {
            finishedAt = 0;
            BidMod.say("\u00a7cAuction cancelled.");
            if (c.showRefunds && !paid.isEmpty()) {
                BidMod.say("\u00a77Refund everyone:");
                paid.forEach((p, v) -> BidMod.say("\u00a77 - /pay " + p + " " + fmt(v)));
            }
            queueLatePaymentRefunds();
            return;
        }
        if (topBidder == null) {
            BidMod.say("\u00a7cAuction over, no bids.");
            if (c.announceEnd) BidMod.chat(fill(c.msgNoBids));
            queueLatePaymentRefunds();
            return;
        }
        BidMod.say("\u00a76SOLD \u00a7f" + itemName + " \u00a7fto \u00a7e" + topBidder + " \u00a7ffor \u00a7a" + c.currency + amountText(topBid));
        if (c.showRefunds) {
            boolean any = false;
            for (Map.Entry<String, Long> e : paid.entrySet()) {
                long refund = e.getValue() - (e.getKey().equals(topBidder) ? topBid : 0);
                if (refund > 0) {
                    if (!any) { BidMod.say("\u00a77Refunds owed:"); any = true; }
                    BidMod.say("\u00a77 - /pay " + e.getKey() + " " + fmt(refund));
                }
            }
        }
        if (c.announceEnd) BidMod.chat(fill(c.msgEnd));
        BidMod.play(SoundEvents.PLAYER_LEVELUP, 1f, c.soundEnd);
        queueLatePaymentRefunds();
    }

    public static String fill(String t) {
        BidConfig c = BidConfig.get();
        Minecraft mc = Minecraft.getInstance();
        String me = mc.player == null ? "" : mc.player.getName().getString();
        return t.replace("{item}", itemName)
                .replace("{bid}", c.currency + amountText(topBid))
                .replace("{bidder}", topBidder == null ? "nobody" : topBidder)
                .replace("{time}", String.valueOf((int) Math.ceil(remainingMs() / 1000.0)))
                .replace("{me}", me);
    }

    // ---------- number helpers ----------
    private static long parse0(String s) {
        long v = parse(s);
        return v < 0 ? 0 : v;
    }

    /** "5k", "1.5m", "2,500" -> long, or -1 if invalid */
    public static long parse(String s) {
        try {
            s = s.trim().replace("$", "").replace(",", "");
            double mult = 1;
            char last = Character.toLowerCase(s.charAt(s.length() - 1));
            if (last == 'k') mult = 1e3;
            else if (last == 'm') mult = 1e6;
            else if (last == 'b') mult = 1e9;
            if (mult != 1) s = s.substring(0, s.length() - 1);
            s = s.replaceAll("[.]+$", "");
            return (long) (Double.parseDouble(s) * mult);
        } catch (Exception e) {
            return -1;
        }
    }

    public static String amountText(long v) {
        return BidConfig.get().amountFormat == 1 ? String.format(Locale.ROOT, "%,d", v) : fmt(v);
    }

    public static String fmt(long v) {
        if (v >= 1_000_000_000L) return trim(v / 1e9) + "B";
        if (v >= 1_000_000L) return trim(v / 1e6) + "M";
        if (v >= 1_000L) return trim(v / 1e3) + "K";
        return String.valueOf(v);
    }

    private static String trim(double d) {
        String s = String.format(Locale.ROOT, "%.2f", d);
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
