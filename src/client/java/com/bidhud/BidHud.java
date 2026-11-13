package com.bidhud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class BidHud {
    public static final class View {
        public ItemStack stack = ItemStack.EMPTY;
        public String item = "", bidder, title = "", timer = "";
        public long bid;
        public float progress = 1f;
        public boolean urgent, ended, demo;
    }

    // ---------- state ----------
    public static boolean visible() {
        BidConfig c = BidConfig.get();
        return Auction.active || (Auction.finishedAt > 0 && System.currentTimeMillis() - Auction.finishedAt < c.endedSeconds * 1000L);
    }

    public static View current() {
        BidConfig c = BidConfig.get();
        View v = new View();
        if (Auction.active) {
            long rem = Auction.remainingMs();
            v.stack = Auction.stack.isEmpty() ? new ItemStack(Items.PAPER) : Auction.stack;
            v.item = Auction.itemName;
            v.bid = Auction.topBid;
            v.bidder = Auction.topBidder;
            v.progress = Auction.isClosing() ? 0f : Math.min(1f, rem / (float) Math.max(1, Auction.durationMs));
            v.urgent = Auction.isClosing() || rem <= c.urgentSeconds * 1000L;
            v.title = Auction.isClosing() ? "BIDS CLOSED" : c.title;
            v.timer = fmtTime(rem);
        } else if (visible()) {
            v.ended = true;
            v.stack = Auction.stack.isEmpty() ? new ItemStack(Items.PAPER) : Auction.stack;
            v.item = Auction.itemName;
            v.bid = Auction.topBid;
            v.bidder = Auction.topBidder;
            v.progress = 0f;
            v.title = c.endedTitle;
            v.timer = Auction.topBidder == null ? "-" : c.soldText;
        } else {
            ItemStack s = Auction.selectedStack();
            v.demo = true;
            v.stack = s.isEmpty() ? new ItemStack(Items.ELYTRA) : s;
            v.item = Auction.nameFor(s);
            v.bid = 1_500_000;
            v.bidder = "Steve";
            v.progress = 0.7f;
            v.title = c.title;
            v.timer = fmtTime(42000);
        }
        return v;
    }

    private static String fmtTime(long ms) {
        BidConfig c = BidConfig.get();
        int s = (int) Math.ceil(ms / 1000.0);
        if (c.timerFormat == 1) return String.format("%d:%02d", s / 60, s % 60);
        return s + c.timerSuffix;
    }

    // ---------- sizing / position ----------
    public static int histH() {
        int n = BidConfig.get().historyLines;
        return n > 0 ? n * 10 + 6 : 0;
    }

    public static int fullH() {
        BidConfig c = BidConfig.get();
        return c.height + (c.historyLines > 0 ? 2 + histH() : 0);
    }

    public static int boxW() { return (int) (BidConfig.get().width * BidConfig.get().scale); }
    public static int boxH() { return (int) (fullH() * BidConfig.get().scale); }

    public static int[] base(int sw, int sh) {
        BidConfig c = BidConfig.get();
        int col = c.anchor % 3, row = c.anchor / 3;
        int bw = boxW(), bh = boxH();
        int x = col == 0 ? 0 : col == 1 ? (sw - bw) / 2 : sw - bw;
        int y = row == 0 ? 0 : row == 1 ? (sh - bh) / 2 : sh - bh;
        return new int[]{x, y};
    }

    public static int[] pos(int sw, int sh) {
        int[] b = base(sw, sh);
        return new int[]{b[0] + BidConfig.get().offsetX, b[1] + BidConfig.get().offsetY};
    }

    // ---------- in-game render ----------
    public static void render(GuiGraphicsExtractor ctx, boolean forceDemo) {
        BidConfig c = BidConfig.get();
        Minecraft mc = Minecraft.getInstance();
        View v = current();
        if (v.demo && !forceDemo && !c.alwaysShow) return;
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        int[] p = pos(sw, sh);
        float dx = 0, dy = 0;
        long now = System.currentTimeMillis();
        if (Auction.active) {
            long age = now - Auction.startedAt;
            if (c.slideIn && age < c.slideMs) {
                float t = age / (float) c.slideMs;
                float e = 1 - (1 - t) * (1 - t) * (1 - t);
                if (c.anchor % 3 == 2) dx = (1 - e) * (sw - p[0] + 10);
                else dx = -(1 - e) * (p[0] + boxW() + 10);
            }
            if (c.shake && Auction.lastBidAt > 0) {
                long ba = now - Auction.lastBidAt;
                if (ba < 500) dx += (float) Math.sin(ba * 0.06) * c.shakeAmount * (1 - ba / 500f);
            }
        }
        draw(ctx, p[0] + dx, p[1] + dy, c.scale, v);
    }

    // ---------- the actual bar ----------
    public static void draw(GuiGraphicsExtractor ctx, float px, float py, float scale, View v) {
        BidConfig c = BidConfig.get();
        Minecraft mc = Minecraft.getInstance();
        Font tr = mc.font;
        long now = System.currentTimeMillis();
        Matrix3x2fStack m = ctx.pose();
        m.pushMatrix();
        m.translate(px, py);
        m.scale(scale, scale);

        int w = c.width, h = c.height;
        int r = Math.min(c.cornerRadius, Math.min(w, h) / 2);
        float ts = c.textScale;
        boolean sh = c.textShadow;

        float flash = 0;
        if (!v.demo && Auction.active && c.flash && Auction.lastBidAt > 0) {
            long ba = now - Auction.lastBidAt;
            if (ba < c.flashMs) flash = 1 - ba / (float) c.flashMs;
        }

        if (c.shadow) rr(ctx, c.shadowOffset, c.shadowOffset, w + c.shadowOffset, h + c.shadowOffset, r, A(c.cShadow), A(c.cShadow));
        if (c.borderThickness > 0) ring(ctx, w, h, r, c.borderThickness, A(c.cBorder));
        rr(ctx, 0, 0, w, h, r, A(c.cBg), A(c.gradient ? c.cBg2 : c.cBg));
        if (c.frosted) {
            rr(ctx, 1, 1, w - 1, Math.max(2, h / 2), Math.max(0, r - 1),
                    withA(0xFFFFFFFF, 28), withA(0xFFFFFFFF, 6));
            ctx.outline(0, 0, w, h, withA(0xFFFFFFFF, 88));
        }

        // accent stripe
        int st = c.stripeThickness;
        switch (c.stripeStyle) {
            case 0 -> ctx.fill(0, r, st, h - r, A(c.cAccent));
            case 1 -> ctx.fill(w - st, r, w, h - r, A(c.cAccent));
            case 2 -> ctx.fill(r, 0, w - r, st, A(c.cAccent));
            case 3 -> ctx.fill(r, h - st, w - r, h, A(c.cAccent));
            default -> {}
        }

        // progress bar
        boolean hasBar = c.barPosition != 2;
        int bth = c.barThickness;
        if (hasBar) {
            int by = c.barPosition == 0 ? h - bth : 0;
            int bx1 = r, bx2 = w - r;
            float p = c.barFill ? 1 - v.progress : v.progress;
            p = Math.max(0f, Math.min(1f, p));
            int fillCol = A(v.urgent && !v.ended ? c.cUrgent : c.cAccent);
            if (c.barStyle == 0) {
                ctx.fill(bx1, by, bx2, by + bth, A(c.cTrack));
                ctx.fill(bx1, by, bx1 + (int) ((bx2 - bx1) * p), by + bth, fillCol);
            } else {
                int span = bx2 - bx1;
                int n = Math.max(1, span / 8);
                for (int i = 0; i < n; i++) {
                    int sx = bx1 + i * span / n;
                    int ex = bx1 + (i + 1) * span / n - 2;
                    ctx.fill(sx, by, ex, by + bth, A(c.cTrack));
                    if (i < Math.ceil(p * n)) ctx.fill(sx, by, ex, by + bth, fillCol);
                }
            }
        }
        int ct = (hasBar && c.barPosition == 1) ? bth : 0;
        int ch = h - (hasBar ? bth : 0);

        // item slot
        int lp = c.padding + (c.stripeStyle == 0 ? st : 0);
        int rp = c.padding + (c.stripeStyle == 1 ? st : 0);
        int tx = lp;
        if (c.showSlot) {
            int size = Math.max(10, Math.min(c.slotSize, ch - 4));
            int sy = ct + (ch - size) / 2;
            rr(ctx, lp, sy, lp + size, sy + size, Math.min(r, 3), A(c.cSlot), A(c.cSlot));
            ctx.outline(lp, sy, size, size, A(c.cSlotBorder));
            if (c.showItem) {
                float is = (size - 4) / 16f;
                float bob = c.bob ? (float) Math.sin(now / (350.0 / Math.max(0.1f, c.bobSpeed))) * c.bobHeight : 0f;
                m.pushMatrix();
                m.translate(lp + (size - 16 * is) / 2f, sy + (size - 16 * is) / 2f + bob);
                m.scale(is, is);
                ctx.item(v.stack, 0, 0);
                if (c.showCount && v.stack.getCount() > 1) ctx.itemDecorations(tr, v.stack, 0, 0);
                m.popMatrix();
            }
            tx = lp + size + c.padding;
        }

        // text
        int tw = w - tx - rp;
        boolean timerTop = c.showTimer && c.timerPos == 0;
        boolean timerBid = c.showTimer && c.timerPos == 1;
        boolean timerBig = c.showTimer && c.timerPos == 2;
        int timerCol = v.ended ? c.cSold
                : v.urgent ? (c.blinkUrgent && (now / 250) % 2 == 0 ? c.cText : c.cUrgent)
                : c.cTimer;
        float bigS = ts * 1.8f;
        float tWs = tr.width(v.timer) * ts;
        float tWb = tr.width(v.timer) * bigS;
        if (timerBig) tw -= (int) tWb + c.padding;

        boolean row1 = c.showTitle || timerTop;
        boolean row2 = c.showName;
        boolean row3 = c.showBid || c.showBidder || timerBid;
        int rows = (row1 ? 1 : 0) + (row2 ? 1 : 0) + (row3 ? 1 : 0);
        float rowH = 11 * ts;
        if (rows > 0) {
            float blockH = rows * rowH - 2 * ts;
            float y = ct + (ch - blockH) / 2f;
            if (row1) {
                if (c.showTitle) txt(ctx, tr, trim(tr, v.title, tw, ts), tx, y, A(c.cAccent), sh, ts);
                if (timerTop) txt(ctx, tr, v.timer, tx + tw - tWs, y, A(timerCol), sh, ts);
                y += rowH;
            }
            if (row2) {
                txt(ctx, tr, trim(tr, applyCase(v.item), tw, ts), tx, y, A(c.cText), sh, ts);
                y += rowH;
            }
            if (row3) {
                float x = tx;
                String bidStr = v.bid > 0 ? c.currency + Auction.amountText(v.bid) : c.noBidText;
                float pop = (c.pop && flash > 0) ? 1 + 0.4f * flash : 1f;
                float right = tx + tw - (timerBid ? tWs + 4 : 0);
                if (c.showBid) {
                    bidStr = trim(tr, bidStr, Math.max(0, right - x), ts * pop);
                    txt(ctx, tr, bidStr, x, y - (pop - 1) * 4 * ts, A(v.ended && v.bid > 0 ? c.cSold : c.cBid), sh, ts * pop);
                    x += tr.width(bidStr) * ts * pop + (c.showBidder ? 4 * ts : 0);
                }
                if (c.showBidder && v.bidder != null && v.bid > 0) {
                    float avail = right - x;
                    if (avail > 8) txt(ctx, tr, trim(tr, c.byText + v.bidder, avail, ts), x, y, A(c.cBidder), sh, ts);
                }
                if (timerBid) txt(ctx, tr, v.timer, tx + tw - tWs, y, A(timerCol), sh, ts);
            }
        }
        if (timerBig) {
            txt(ctx, tr, v.timer, w - rp - tWb, ct + (ch - 9 * bigS) / 2f, A(timerCol), sh, bigS);
        }

        if (flash > 0) {
            int fc = withA(c.cFlash, (int) (110 * flash));
            rr(ctx, 0, 0, w, h, r, fc, fc);
        }

        // bid history under the bar
        if (c.historyLines > 0) {
            int hy = h + 2, hh = histH();
            rr(ctx, 0, hy, w, hy + hh, Math.min(r, 4), A(c.cBg), A(c.cBg));
            List<String[]> list = new ArrayList<>();
            if (v.demo) {
                list.add(new String[]{"Steve", "1.5M"});
                list.add(new String[]{"Alex", "1.2M"});
                list.add(new String[]{"Pro_Gamer", "900K"});
            } else {
                for (Auction.Bid b : Auction.history) list.add(new String[]{b.player(), Auction.amountText(b.amount())});
            }
            for (int i = 0; i < Math.min(c.historyLines, list.size()); i++) {
                float ly = hy + 4 + i * 10;
                txt(ctx, tr, list.get(i)[0], 6, ly, A(c.cSub), false, 0.85f);
                String a = c.currency + list.get(i)[1];
                txt(ctx, tr, a, w - 6 - tr.width(a) * 0.85f, ly, A(c.cBid), false, 0.85f);
            }
        }
        m.popMatrix();
    }

    // ---------- helpers ----------
    private static String applyCase(String s) {
        int k = BidConfig.get().nameCase;
        return k == 1 ? s.toUpperCase() : k == 2 ? s.toLowerCase() : s;
    }

    private static String trim(Font tr, String s, float maxW, float ts) {
        int mw = (int) (maxW / ts);
        return tr.width(s) <= mw ? s : tr.plainSubstrByWidth(s, Math.max(0, mw));
    }

    private static void txt(GuiGraphicsExtractor ctx, Font tr, String s, float x, float y, int col, boolean shadow, float sc) {
        Matrix3x2fStack m = ctx.pose();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(sc, sc);
        ctx.text(tr, s, 0, 0, col, shadow);
        m.popMatrix();
    }

    public static int A(int color) {
        int a = (int) ((color >>> 24) * BidConfig.get().opacity / 100f);
        return (a << 24) | (color & 0xFFFFFF);
    }

    public static int withA(int color, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (color & 0xFFFFFF);
    }

    public static int lerp(int c1, int c2, float t) {
        int a = (int) ((c1 >>> 24) + ((c2 >>> 24) - (c1 >>> 24)) * t);
        int r = (int) (((c1 >> 16) & 255) + (((c2 >> 16) & 255) - ((c1 >> 16) & 255)) * t);
        int g = (int) (((c1 >> 8) & 255) + (((c2 >> 8) & 255) - ((c1 >> 8) & 255)) * t);
        int b = (int) ((c1 & 255) + ((c2 & 255) - (c1 & 255)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int insetAt(int row, int totalH, int radius) {
        if (radius <= 0) return 0;
        int d = row < radius ? radius - row : (row >= totalH - radius ? row - (totalH - radius) + 1 : 0);
        if (d <= 0) return 0;
        double dy = d - 0.5;
        return radius - (int) Math.round(Math.sqrt(Math.max(0, radius * radius - dy * dy)));
    }

    /** rounded rect with optional vertical gradient */
    public static void rr(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int r, int c1, int c2) {
        int h = y2 - y1;
        if (r <= 0 && c1 == c2) { ctx.fill(x1, y1, x2, y2, c1); return; }
        if (r <= 0) { ctx.fillGradient(x1, y1, x2, y2, c1, c2); return; }
        for (int i = 0; i < h; i++) {
            int in = insetAt(i, h, r);
            int col = c1 == c2 ? c1 : lerp(c1, c2, i / (float) Math.max(1, h - 1));
            ctx.fill(x1 + in, y1 + i, x2 - in, y1 + i + 1, col);
        }
    }

    private static void ring(GuiGraphicsExtractor ctx, int w, int h, int r, int bt, int col) {
        for (int y = -bt; y < h + bt; y++) {
            int oi = insetAt(y + bt, h + 2 * bt, r + bt);
            int ox1 = -bt + oi, ox2 = w + bt - oi;
            if (y < 0 || y >= h) {
                ctx.fill(ox1, y, ox2, y + 1, col);
            } else {
                int ii = insetAt(y, h, r);
                if (ii > ox1) ctx.fill(ox1, y, ii, y + 1, col);
                if (ox2 > w - ii) ctx.fill(w - ii, y, ox2, y + 1, col);
            }
        }
    }
}
