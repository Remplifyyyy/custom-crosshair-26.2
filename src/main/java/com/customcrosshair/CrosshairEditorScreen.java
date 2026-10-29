package com.customcrosshair;

import com.customcrosshair.CrosshairConfig.Profile;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import static com.customcrosshair.CrosshairConfig.CENTER;
import static com.customcrosshair.CrosshairConfig.GRID;

/** The menu: a pixel canvas on the left, settings on the right. */
public class CrosshairEditorScreen extends Screen {

    private enum Symmetry {
        OFF("Off"), HORIZONTAL("Mirror L/R"), VERTICAL("Mirror U/D"), FOUR("4-way"), EIGHT("8-way");
        final String label;
        Symmetry(String l) { label = l; }
        Symmetry next() { return values()[(ordinal() + 1) % values().length]; }
    }

    private static final int[] SWATCHES = {
            0xFFFFFF, 0xC0C0C0, 0x808080, 0x000000, 0xFF3030, 0xFF8C00, 0xFFE030, 0x80FF20,
            0x20B020, 0x20E0E0, 0x40A0FF, 0x2040FF, 0x9030FF, 0xFF30FF, 0xFF90C0, 0x8B5A2B
    };

    // remembered between openings
    private static boolean editingTarget = false;
    private static boolean hitboxTab = false;
    private static boolean editingOutColor = false;   // hitbox tab: which colour the sliders edit
    private static Symmetry symmetry = Symmetry.OFF;
    private static Presets nextPreset = Presets.PLUS;

    private final Screen parent;
    private CrosshairConfig cfg;

    private int cell, canvasX, canvasY, canvasSize;
    private int col1X, col2X, colW;
    private int swatchY, swatchSize, previewY, previewSize;

    private boolean syncingSize = false;
    private boolean strokeInCanvas = false;
    private boolean strokeErases = false;
    private int lastCellX = -1, lastCellY = -1;

    public CrosshairEditorScreen(Screen parent) {
        super(Component.literal("Remplify Crosshair Indicator"));
        this.parent = parent;
    }

    private Profile current() { return editingTarget ? cfg.target : cfg.normal; }

    /** The drawing shown on the canvas for the current tab. */
    private Profile shapeOwner() { return (editingTarget && cfg.targetUsesNormalShape) ? cfg.normal : current(); }

    private boolean canDraw() { return !(editingTarget && cfg.targetUsesNormalShape); }

    @Override
    protected void init() {
        cfg = CrosshairConfig.get();

        cell = Math.max(4, Math.min(10, (height - 64) / GRID));
        canvasSize = cell * GRID;
        canvasX = 12;
        canvasY = 32;

        col1X = canvasX + canvasSize + 12;
        colW = Math.max(90, Math.min(150, (width - col1X - 20) / 2));
        col2X = col1X + colW + 8;
        int row = 22, h = 20;

        // ---- tabs
        Button normalTab = Button.builder(Component.literal("Crosshair"), b -> switchTab(0))
                .bounds(12, 6, 96, h).build();
        Button targetTab = Button.builder(Component.literal("Entity indicator"), b -> switchTab(1))
                .bounds(112, 6, 96, h).build();
        Button hitTab = Button.builder(Component.literal("Hitboxes"), b -> switchTab(2))
                .bounds(212, 6, 96, h).build();
        normalTab.active = hitboxTab || editingTarget;
        targetTab.active = hitboxTab || !editingTarget;
        hitTab.active = !hitboxTab;
        addRenderableWidget(normalTab);
        addRenderableWidget(targetTab);
        addRenderableWidget(hitTab);
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(width - 62, 6, 50, h).build());

        if (hitboxTab) { initHitboxTab(row, h); return; }

        // ---- column 1: drawing tools
        int y = canvasY;
        Button preset = Button.builder(Component.literal("Preset: " + nextPreset.label), b -> {
            shapeOwner().pixels = nextPreset.make();
            nextPreset = nextPreset.next();
            b.setMessage(Component.literal("Preset: " + nextPreset.label));
        }).bounds(col1X, y, colW - 52, h).build();
        preset.active = canDraw();
        addRenderableWidget(preset);
        Button saved = Button.builder(Component.literal("Saved"), b -> {
            if (minecraft != null) minecraft.gui.setScreen(new SavedPresetsScreen(this, shapeOwner()));
        }).bounds(col1X + colW - 48, y, 48, h).build();
        saved.active = canDraw();
        addRenderableWidget(saved);
        y += row;

        int half = (colW - 4) / 2;
        Button clear = Button.builder(Component.literal("Clear"), b -> shapeOwner().pixels = new boolean[GRID * GRID])
                .bounds(col1X, y, half, h).build();
        Button invert = Button.builder(Component.literal("Invert"), b -> {
            boolean[] p = shapeOwner().pixels;
            for (int i = 0; i < p.length; i++) p[i] = !p[i];
        }).bounds(col1X + half + 4, y, colW - half - 4, h).build();
        clear.active = invert.active = canDraw();
        addRenderableWidget(clear);
        addRenderableWidget(invert);
        y += row;

        Button sym = Button.builder(Component.literal("Symmetry: " + symmetry.label), b -> {
            symmetry = symmetry.next();
            b.setMessage(Component.literal("Symmetry: " + symmetry.label));
        }).bounds(col1X, y, colW, h).build();
        sym.active = canDraw();
        addRenderableWidget(sym);
        y += row;

        // size: slider (1% - 500%) plus a box you can type an exact percentage into
        int fieldW = 34;
        SizeSlider sizeSlider = new SizeSlider(col1X, y, colW - fieldW - 4, h, Math.round(current().scale * 100f));
        EditBox sizeField = new EditBox(font, col1X + colW - fieldW, y, fieldW, h, Component.literal("Size %"));
        sizeField.setMaxLength(3);
        sizeField.setFilter(t -> t.matches("\\d{0,3}"));
        sizeField.setValue(String.valueOf(Math.round(current().scale * 100f)));
        sizeField.setResponder(t -> {
            if (syncingSize || t.isEmpty()) return;
            int pct = Math.max(1, Math.min(500, Integer.parseInt(t)));
            current().scale = pct / 100f;
            syncingSize = true;
            sizeSlider.setPercent(pct);
            syncingSize = false;
        });
        sizeSlider.onChange = pct -> {
            current().scale = pct / 100f;
            if (syncingSize) return;
            syncingSize = true;
            sizeField.setValue(String.valueOf(pct));
            syncingSize = false;
        };
        addRenderableWidget(sizeSlider);
        addRenderableWidget(sizeField);
        y += row;

        addRenderableWidget(Button.builder(onOff("Outline", current().outline), b -> {
            current().outline = !current().outline;
            b.setMessage(onOff("Outline", current().outline));
        }).bounds(col1X, y, colW, h).build());
        y += row;

        if (!editingTarget) {
            addRenderableWidget(Button.builder(Component.literal("3rd person: " + (cfg.hideInThirdPerson ? "Hide" : "Show")), b -> {
                cfg.hideInThirdPerson = !cfg.hideInThirdPerson;
                b.setMessage(Component.literal("3rd person: " + (cfg.hideInThirdPerson ? "Hide" : "Show")));
            }).bounds(col1X, y, colW, h).build());
        } else {
            addRenderableWidget(Button.builder(onOff("Indicator", cfg.targetEnabled), b -> {
                cfg.targetEnabled = !cfg.targetEnabled;
                b.setMessage(onOff("Indicator", cfg.targetEnabled));
            }).bounds(col1X, y, colW, h).build());
            y += row;
            addRenderableWidget(Button.builder(Component.literal("Targets: " + cfg.targetMode.label), b -> {
                cfg.targetMode = cfg.targetMode.next();
                b.setMessage(Component.literal("Targets: " + cfg.targetMode.label));
            }).bounds(col1X, y, colW, h).build());
            y += row;
            addRenderableWidget(Button.builder(Component.literal(shapeLabel()), b -> {
                cfg.targetUsesNormalShape = !cfg.targetUsesNormalShape;
                if (!cfg.targetUsesNormalShape && isEmpty(cfg.target.pixels)) cfg.target.pixels = cfg.normal.pixels.clone();
                rebuildWidgets();
            }).bounds(col1X, y, colW, h).build());
            y += row;
            Button copy = Button.builder(Component.literal("Copy normal drawing"), b ->
                    cfg.target.pixels = cfg.normal.pixels.clone()).bounds(col1X, y, colW, h).build();
            copy.active = canDraw();
            addRenderableWidget(copy);
        }

        // ---- column 2: colour + preview
        y = canvasY;
        addRenderableWidget(Button.builder(styleLabel(), b -> {
            current().vanillaStyle = !current().vanillaStyle;
            rebuildWidgets();
        }).bounds(col2X, y, colW, h).build());
        y += row;
        boolean solid = !current().vanillaStyle;
        IntSlider[] colour = {
                new IntSlider(col2X, y, colW, h, "Red", 0, 255,
                        () -> (current().color >> 16) & 0xFF, v -> setChannel(16, v)),
                new IntSlider(col2X, y + row, colW, h, "Green", 0, 255,
                        () -> (current().color >> 8) & 0xFF, v -> setChannel(8, v)),
                new IntSlider(col2X, y + row * 2, colW, h, "Blue", 0, 255,
                        () -> current().color & 0xFF, v -> setChannel(0, v)),
                new IntSlider(col2X, y + row * 3, colW, h, "Opacity", 20, 255,
                        () -> (current().color >>> 24) & 0xFF, v -> setChannel(24, v))
        };
        for (IntSlider sl : colour) { sl.active = solid; addRenderableWidget(sl); }
        y += row * 4;

        swatchY = y + 2;
        swatchSize = Math.max(6, (colW - 7) / 8);
        previewY = swatchY + swatchSize * 2 + 16;
        previewSize = Math.max(40, Math.min(colW, height - previewY - 8));
    }

    private void switchTab(int tab) {
        hitboxTab = tab == 2;
        if (tab != 2) editingTarget = tab == 1;
        rebuildWidgets();
    }

    // ------------------------------------------------------------------ hitboxes tab

    private int hbX, hbY;

    private void initHitboxTab(int row, int h) {
        int x1 = 12, w = Math.max(120, Math.min(170, (width - 40) / 2)), x2 = x1 + w + 12;
        int y = 32;

        // same switch as F3+B
        addRenderableWidget(Button.builder(Component.literal(VanillaHitboxes.isOn() ? "Hitboxes: ON" : "Hitboxes: OFF"), b -> {
            VanillaHitboxes.toggle();
            b.setMessage(Component.literal(VanillaHitboxes.isOn() ? "Hitboxes: ON" : "Hitboxes: OFF"));
        }).bounds(x1, y, w, h).build());
        y += row;
        addRenderableWidget(Button.builder(Component.literal("Show: " + cfg.hitboxTargets.label), b -> {
            cfg.hitboxTargets = cfg.hitboxTargets.next();
            b.setMessage(Component.literal("Show: " + cfg.hitboxTargets.label));
        }).bounds(x1, y, w, h).build());
        y += row;
        addRenderableWidget(Button.builder(Component.literal("Other boxes: " + (cfg.hitboxShowOutOfRange ? "Show" : "Hide")), b -> {
            cfg.hitboxShowOutOfRange = !cfg.hitboxShowOutOfRange;
            b.setMessage(Component.literal("Other boxes: " + (cfg.hitboxShowOutOfRange ? "Show" : "Hide")));
        }).bounds(x1, y, w, h).build());
        y += row;
        addRenderableWidget(Button.builder(onOff("Through walls", cfg.hitboxThroughWalls), b -> {
            cfg.hitboxThroughWalls = !cfg.hitboxThroughWalls;
            b.setMessage(onOff("Through walls", cfg.hitboxThroughWalls));
        }).bounds(x1, y, w, h).build());
        y += row;
        addRenderableWidget(new IntSlider(x1, y, w, h, "Line width", 1, 10,
                () -> Math.round(cfg.hitboxWidth), v -> cfg.hitboxWidth = v));
        y += row;
        addRenderableWidget(Button.builder(onOff("F3+B lines", cfg.vanillaHitboxDirection), b -> {
            cfg.vanillaHitboxDirection = !cfg.vanillaHitboxDirection;
            b.setMessage(onOff("F3+B lines", cfg.vanillaHitboxDirection));
        }).bounds(x1, y, w, h).build());
        y += row;
        addRenderableWidget(Button.builder(onOff("Show feedback", cfg.showToggleFeedback), b -> {
            cfg.showToggleFeedback = !cfg.showToggleFeedback;
            b.setMessage(onOff("Show feedback", cfg.showToggleFeedback));
        }).bounds(x1, y, w, h).build());
        y += row;
        hbY = y + 6;

        // colours
        y = 32;
        addRenderableWidget(Button.builder(Component.literal("Editing: " + (editingOutColor ? "Other colour" : "Looking-at colour")), b -> {
            editingOutColor = !editingOutColor;
            rebuildWidgets();
        }).bounds(x2, y, w, h).build());
        y += row;
        addRenderableWidget(new IntSlider(x2, y, w, h, "Red", 0, 255, () -> (editColor() >> 16) & 0xFF, v -> setChannel(16, v)));
        y += row;
        addRenderableWidget(new IntSlider(x2, y, w, h, "Green", 0, 255, () -> (editColor() >> 8) & 0xFF, v -> setChannel(8, v)));
        y += row;
        addRenderableWidget(new IntSlider(x2, y, w, h, "Blue", 0, 255, () -> editColor() & 0xFF, v -> setChannel(0, v)));
        y += row;
        addRenderableWidget(new IntSlider(x2, y, w, h, "Opacity", 20, 255, () -> (editColor() >>> 24) & 0xFF, v -> setChannel(24, v)));
        y += row;

        col2X = x2;
        colW = w;
        swatchY = y + 2;
        swatchSize = Math.max(6, (w - 7) / 8);
        hbX = x1;
    }

    private void renderHitboxTab(GuiGraphicsExtractor ctx) {
        int y = hbY;

        // little preview of the two box colours
        int bx = hbX, by = y, bw = 18, bh = 30;
        drawPreviewBox(ctx, bx, by, bw, bh, cfg.hitboxColor);
        ctx.text(font, Component.literal("Looking at"), bx, by + bh + 3, 0xFFFFFFFF, true);
        if (cfg.hitboxShowOutOfRange) {
            drawPreviewBox(ctx, bx + 70, by, bw, bh, cfg.hitboxOutColor);
            ctx.text(font, Component.literal("Other"), bx + 70, by + bh + 3, 0xFFFFFFFF, true);
        }
        drawSwatches(ctx);
    }

    private static void drawPreviewBox(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + h, 0x40000000);
        outlineBox(ctx, x, y, w, h, color);
        outlineBox(ctx, x + 1, y + 1, w - 2, h - 2, color);
    }

    private int editColor() {
        if (hitboxTab) return editingOutColor ? cfg.hitboxOutColor : cfg.hitboxColor;
        return current().color;
    }

    private void setEditColor(int c) {
        if (hitboxTab) {
            if (editingOutColor) cfg.hitboxOutColor = c; else cfg.hitboxColor = c;
        } else {
            current().color = c;
        }
    }

    private String shapeLabel() {
        return "Shape: " + (cfg.targetUsesNormalShape ? "Same as normal" : "Own drawing");
    }

    private Component styleLabel() {
        return Component.literal("Style: " + (current().vanillaStyle ? "Vanilla (see-through)" : "Solid colour"));
    }

    private static Component onOff(String name, boolean v) { return Component.literal(name + ": " + (v ? "On" : "Off")); }

    private void setChannel(int shift, int v) {
        setEditColor((editColor() & ~(0xFF << shift)) | ((v & 0xFF) << shift));
    }

    private static boolean isEmpty(boolean[] px) {
        for (boolean b : px) if (b) return false;
        return true;
    }

    // ------------------------------------------------------------------ drawing on the canvas

    private boolean inCanvas(int mx, int my) {
        return mx >= canvasX && my >= canvasY && mx < canvasX + canvasSize && my < canvasY + canvasSize;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        int mx = (int) event.x(), my = (int) event.y();
        boolean left = event.button() == 0;
        if (event.button() != 0 && event.button() != 1) return false;
        lastCellX = lastCellY = -1;
        if (left && (hitboxTab || !current().vanillaStyle) && clickSwatch(mx, my)) return true;
        if (!hitboxTab && canDraw() && inCanvas(mx, my)) {
            strokeInCanvas = true;
            strokeErases = !left;
            paintAt(mx, my);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (strokeInCanvas) {
            paintAt((int) event.x(), (int) event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        strokeInCanvas = false;
        return super.mouseReleased(event);
    }

    private void paintAt(int mx, int my) {
        int cx = Math.max(0, Math.min(GRID - 1, (mx - canvasX) / cell));
        int cy = Math.max(0, Math.min(GRID - 1, (my - canvasY) / cell));
        if (lastCellX < 0) paint(cx, cy, !strokeErases);
        else line(lastCellX, lastCellY, cx, cy, !strokeErases);
        lastCellX = cx;
        lastCellY = cy;
    }

    /** Paint a straight line so fast mouse moves don't leave gaps. */
    private void line(int x0, int y0, int x1, int y1, boolean value) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
        while (true) {
            paint(x0, y0, value);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }

    private void paint(int x, int y, boolean value) {
        Profile p = shapeOwner();
        int mx = GRID - 1 - x, my = GRID - 1 - y;
        p.set(x, y, value);
        switch (symmetry) {
            case HORIZONTAL -> p.set(mx, y, value);
            case VERTICAL -> p.set(x, my, value);
            case FOUR -> { p.set(mx, y, value); p.set(x, my, value); p.set(mx, my, value); }
            case EIGHT -> {
                p.set(mx, y, value); p.set(x, my, value); p.set(mx, my, value);
                p.set(y, x, value); p.set(my, x, value); p.set(y, mx, value); p.set(my, mx, value);
            }
            default -> { }
        }
    }

    private boolean clickSwatch(int mx, int my) {
        for (int i = 0; i < SWATCHES.length; i++) {
            int sx = col2X + (i % 8) * (swatchSize + 1);
            int sy = swatchY + (i / 8) * (swatchSize + 1);
            if (mx >= sx && my >= sy && mx < sx + swatchSize && my < sy + swatchSize) {
                setEditColor((editColor() & 0xFF000000) | SWATCHES[i]);
                rebuildWidgets();                     // refresh the RGB sliders
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        if (hitboxTab) { renderHitboxTab(ctx); return; }

        Profile look = current();
        boolean[] shape = shapeOwner().pixels;

        // canvas
        ctx.fill(canvasX - 1, canvasY - 1, canvasX + canvasSize + 1, canvasY + canvasSize + 1, 0xFF000000);
        for (int y = 0; y < GRID; y++) for (int x = 0; x < GRID; x++) {
            int px = canvasX + x * cell, py = canvasY + y * cell;
            int bg = ((x + y) & 1) == 0 ? 0xFF2B2B2B : 0xFF333333;
            if (x == CENTER && y == CENTER) bg = 0xFF4A4A4A;   // marks the middle of the screen
            ctx.fill(px, py, px + cell, py + cell, bg);
            if (shape[y * GRID + x]) ctx.fill(px, py, px + cell, py + cell, look.vanillaStyle ? 0xFFDDDDDD : look.color);
        }
        if (canDraw() && inCanvas(mouseX, mouseY)) {
            int hx = canvasX + (mouseX - canvasX) / cell * cell, hy = canvasY + (mouseY - canvasY) / cell * cell;
            outlineBox(ctx, hx, hy, cell, cell, 0xFFFFFFFF);
        }

        int textY = canvasY + canvasSize + 5;
        if (canDraw()) {
            ctx.text(font, Component.literal("Left: draw  Right: erase"), canvasX, textY, 0xFFAAAAAA, true);
        } else {
            ctx.text(font, Component.literal("Using normal drawing"), canvasX, textY, 0xFFFFCC55, true);
            ctx.text(font, Component.literal("(change \"Shape\" to draw)"), canvasX, textY + 10, 0xFFAAAAAA, true);
        }

        // colour swatches (only used in solid colour style)
        if (!look.vanillaStyle) drawSwatches(ctx);

        // live preview at real in-game size
        int pvY = previewY;
        ctx.text(font, Component.literal(editingTarget ? "Preview (on target)" : "Preview"), col2X, pvY - 11, 0xFFFFFFFF, true);
        ctx.fillGradient(col2X, pvY, col2X + previewSize, pvY + previewSize, 0xFF6FA8DC, 0xFF5D8A3A);
        outlineBox(ctx, col2X - 1, pvY - 1, previewSize + 2, previewSize + 2, 0xFF000000);
        CrosshairRenderer.draw(ctx, shape, look, col2X + previewSize / 2, pvY + previewSize / 2, look.scale);
    }

    private void drawSwatches(GuiGraphicsExtractor ctx) {
        for (int i = 0; i < SWATCHES.length; i++) {
            int sx = col2X + (i % 8) * (swatchSize + 1);
            int sy = swatchY + (i / 8) * (swatchSize + 1);
            ctx.fill(sx, sy, sx + swatchSize, sy + swatchSize, 0xFF000000 | SWATCHES[i]);
            if ((editColor() & 0xFFFFFF) == SWATCHES[i]) outlineBox(ctx, sx - 1, sy - 1, swatchSize + 2, swatchSize + 2, 0xFFFFFFFF);
        }
    }

    private static void outlineBox(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public void onClose() {
        CrosshairConfig.save();
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    @Override
    public void removed() {
        CrosshairConfig.save();
        super.removed();
    }

    // ------------------------------------------------------------------ slider helper

    /** Crosshair size as a percentage of the vanilla size: 1% - 500%. */
    private static class SizeSlider extends AbstractSliderButton {
        private static final int MIN = 1, MAX = 500;
        java.util.function.IntConsumer onChange = p -> { };

        SizeSlider(int x, int y, int w, int h, int percent) {
            super(x, y, w, h, Component.empty(), toValue(percent));
            updateMessage();
        }

        private static double toValue(int percent) {
            return (Math.max(MIN, Math.min(MAX, percent)) - MIN) / (double) (MAX - MIN);
        }

        int percent() { return MIN + (int) Math.round(value * (MAX - MIN)); }

        /** Moves the slider when a number is typed in the box. */
        void setPercent(int percent) {
            value = toValue(percent);
            updateMessage();
        }

        @Override
        protected void updateMessage() { setMessage(Component.literal("Size: " + percent() + "%")); }

        @Override
        protected void applyValue() { onChange.accept(percent()); }
    }

    private static class IntSlider extends AbstractSliderButton {
        private final String name;
        private final int min, max;
        private final IntConsumer setter;

        IntSlider(int x, int y, int w, int h, String name, int min, int max, IntSupplier getter, IntConsumer setter) {
            super(x, y, w, h, Component.empty(), (getter.getAsInt() - min) / (double) (max - min));
            this.name = name;
            this.min = min;
            this.max = max;
            this.setter = setter;
            updateMessage();
        }

        private int current() { return min + (int) Math.round(value * (max - min)); }

        @Override
        protected void updateMessage() { setMessage(Component.literal(name + ": " + current())); }

        @Override
        protected void applyValue() { setter.accept(current()); }
    }
}
