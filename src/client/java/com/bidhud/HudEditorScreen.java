package com.bidhud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

public class HudEditorScreen extends Screen {
    private static final String[] ANCHORS = {"Top left", "Top center", "Top right", "Middle left", "Center",
            "Middle right", "Bottom left", "Bottom center", "Bottom right"};
    private static final int RESIZE_HANDLE_SIZE = 10;
    private final Screen parent;
    private boolean dragging;
    private boolean resizing;
    private double offX, offY, resizeStartX, resizeStartY;
    private int resizeStartWidth, resizeStartHeight, resizeOriginX, resizeOriginY;

    public HudEditorScreen(Screen parent) {
        super(Component.literal("Bid HUD position"));
        this.parent = parent;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0x88000000);
        BidHud.render(ctx, true);
        BidConfig c = BidConfig.get();
        int[] p = BidHud.pos(width, height);
        ctx.outline(p[0] - 2, p[1] - 2, BidHud.boxW() + 4, BidHud.boxH() + 4, 0xFFFFFFFF);
        int handleX = p[0] + BidHud.boxW() - RESIZE_HANDLE_SIZE;
        int handleY = p[1] + BidHud.boxH() - RESIZE_HANDLE_SIZE;
        ctx.fill(handleX, handleY, handleX + RESIZE_HANDLE_SIZE, handleY + RESIZE_HANDLE_SIZE, 0xDD101018);
        int gripColor = 0xFF000000 | (c.cAccent & 0xFFFFFF);
        ctx.fill(handleX + 7, handleY + 1, handleX + 9, handleY + 3, gripColor);
        ctx.fill(handleX + 5, handleY + 3, handleX + 9, handleY + 5, gripColor);
        ctx.fill(handleX + 3, handleY + 5, handleX + 9, handleY + 7, gripColor);
        ctx.fill(handleX + 1, handleY + 7, handleX + 9, handleY + 9, gripColor);

        String[] lines = {
            "Hold left click and drag the bar to move it",
            "Drag the bottom-right corner to resize  |  Scroll = scale",
            "Arrows = nudge 1px  |  A = change anchor (" + ANCHORS[c.anchor] + ")  |  R = reset  |  ESC = back"
        };
        for (int i = 0; i < lines.length; i++) {
            int tw = font.width(lines[i]);
            ctx.text(font, lines[i], (width - tw) / 2, height / 2 - 16 + i * 12, 0xFFFFFFFF, true);
        }
    }

    private boolean inside(double mx, double my) {
        int[] p = BidHud.pos(width, height);
        return mx >= p[0] - 4 && mx <= p[0] + BidHud.boxW() + 4
                && my >= p[1] - 4 && my <= p[1] + BidHud.boxH() + 4;
    }

    private boolean onResizeHandle(double mx, double my) {
        int[] p = BidHud.pos(width, height);
        int x = p[0] + BidHud.boxW();
        int y = p[1] + BidHud.boxH();
        return mx >= x - RESIZE_HANDLE_SIZE && mx <= x + RESIZE_HANDLE_SIZE
                && my >= y - RESIZE_HANDLE_SIZE && my <= y + RESIZE_HANDLE_SIZE;
    }

    private void moveBar(double mx, double my) {
        BidConfig c = BidConfig.get();
        int[] b = BidHud.base(width, height);
        c.offsetX = (int) (mx - offX) - b[0];
        c.offsetY = (int) (my - offY) - b[1];
        clamp();
    }

    private void resizeBar(double mx, double my) {
        BidConfig c = BidConfig.get();
        c.width = Math.max(160, Math.min(360,
                resizeStartWidth + (int) Math.round((mx - resizeStartX) / c.scale)));
        c.height = Math.max(30, Math.min(100,
                resizeStartHeight + (int) Math.round((my - resizeStartY) / c.scale)));
        int[] b = BidHud.base(width, height);
        c.offsetX = resizeOriginX - b[0];
        c.offsetY = resizeOriginY - b[1];
        clamp();
    }

    private void clamp() {
        BidConfig c = BidConfig.get();
        int[] b = BidHud.base(width, height);
        c.offsetX = Math.max(-b[0], Math.min(width - BidHud.boxW() - b[0], c.offsetX));
        c.offsetY = Math.max(-b[1], Math.min(height - BidHud.boxH() - b[1], c.offsetY));
        c.markDirty();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        if (event.button() == 1 && onResizeHandle(mx, my)) {
            BidConfig c = BidConfig.get();
            int[] p = BidHud.pos(width, height);
            resizing = true;
            resizeStartX = mx;
            resizeStartY = my;
            resizeStartWidth = c.width;
            resizeStartHeight = c.height;
            resizeOriginX = p[0];
            resizeOriginY = p[1];
            return true;
        }
        if (event.button() == 1 && inside(mx, my)) {
            int[] p = BidHud.pos(width, height);
            dragging = true;
            offX = mx - p[0];
            offY = my - p[1];
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (resizing) {
            resizeBar(event.x(), event.y());
            return true;
        }
        if (dragging) {
            moveBar(event.x(), event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public void mouseMoved(double mx, double my) {
        super.mouseMoved(mx, my);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        resizing = false;
        BidConfig.get().saveIfDirty();
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        BidConfig c = BidConfig.get();
        c.scale = Math.max(0.5f, Math.min(3f, c.scale + (float) v * 0.05f));
        clamp();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (BidMod.menuKeyPressed(event)) {
            onClose();
            return true;
        }
        int key = event.keycode();
        BidConfig c = BidConfig.get();
        if (event.isEscape()) {
            onClose();
            return true;
        }
        switch (key) {
            case InputConstants.KEY_LEFT -> c.offsetX--;
            case InputConstants.KEY_RIGHT -> c.offsetX++;
            case InputConstants.KEY_UP -> c.offsetY--;
            case InputConstants.KEY_DOWN -> c.offsetY++;
            case InputConstants.KEY_R -> c.reset("anchor", "offsetX", "offsetY", "scale");
            case InputConstants.KEY_A -> {
                int[] p = BidHud.pos(width, height);
                c.anchor = (c.anchor + 1) % 9;
                int[] b = BidHud.base(width, height);
                c.offsetX = p[0] - b[0];
                c.offsetY = p[1] - b[1];
            }
            default -> { return super.keyPressed(event); }
        }
        clamp();
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public void removed() {
        BidConfig.get().save();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
