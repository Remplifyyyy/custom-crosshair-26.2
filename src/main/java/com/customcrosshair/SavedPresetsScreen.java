package com.customcrosshair;

import com.customcrosshair.CrosshairConfig.Profile;
import com.customcrosshair.CrosshairConfig.SavedPreset;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.List;

import static com.customcrosshair.CrosshairConfig.GRID;

/** Save the current drawing under a name, and load or delete your saved drawings. */
public class SavedPresetsScreen extends Screen {
    private static final int ROW = 22;
    private static int page = 0;

    private final Screen parent;
    private final Profile drawing;   // the drawing being edited in the editor

    private EditBox nameField;
    private String typedName = "";
    private int pendingDelete = -1;

    private int left, listTop, perPage;
    private static final int LIST_W = 300;

    public SavedPresetsScreen(Screen parent, Profile drawing) {
        super(Component.literal("Saved crosshairs"));
        this.parent = parent;
        this.drawing = drawing;
    }

    private List<SavedPreset> presets() { return CrosshairConfig.get().savedPresets; }

    @Override
    protected void init() {
        left = (width - LIST_W) / 2;
        int y = 24;

        // ---- save current drawing
        nameField = new EditBox(font, left, y, LIST_W - 104, 20, Component.literal("Name"));
        nameField.setMaxLength(24);
        nameField.setHint(Component.literal("Name your crosshair..."));
        nameField.setValue(typedName);
        nameField.setResponder(t -> typedName = t);
        addRenderableWidget(nameField);
        addRenderableWidget(Button.builder(Component.literal("Save current"), b -> saveCurrent())
                .bounds(left + LIST_W - 100, y, 100, 20).build());

        // ---- list of saved drawings
        listTop = y + 30;
        perPage = Math.max(1, (height - listTop - 34) / ROW);
        int pages = Math.max(1, (presets().size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pages - 1));

        int start = page * perPage;
        for (int i = start; i < Math.min(presets().size(), start + perPage); i++) {
            final int index = i;
            int ry = listTop + (i - start) * ROW;
            addRenderableWidget(Button.builder(Component.literal("Load"), b -> load(index))
                    .bounds(left + LIST_W - 104, ry, 50, 20).build());
            addRenderableWidget(Button.builder(Component.literal(pendingDelete == index ? "Sure?" : "Delete"), b -> delete(index))
                    .bounds(left + LIST_W - 50, ry, 50, 20).build());
        }

        // ---- page buttons + back
        int by = height - 26;
        Button prev = Button.builder(Component.literal("<"), b -> { page--; pendingDelete = -1; rebuildWidgets(); })
                .bounds(left, by, 20, 20).build();
        Button next = Button.builder(Component.literal(">"), b -> { page++; pendingDelete = -1; rebuildWidgets(); })
                .bounds(left + 84, by, 20, 20).build();
        prev.active = page > 0;
        next.active = page < pages - 1;
        addRenderableWidget(prev);
        addRenderableWidget(next);
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(left + LIST_W - 100, by, 100, 20).build());
    }

    private void saveCurrent() {
        String name = typedName.trim();
        if (name.isEmpty()) name = "Crosshair " + (presets().size() + 1);
        // same name = overwrite that one
        for (SavedPreset p : presets()) {
            if (p.name.equalsIgnoreCase(name)) {
                p.pixels = drawing.pixels.clone();
                finishSave();
                return;
            }
        }
        presets().add(new SavedPreset(name, drawing.pixels));
        page = (presets().size() - 1) / perPage;   // jump to where it was added
        finishSave();
    }

    private void finishSave() {
        typedName = "";
        pendingDelete = -1;
        CrosshairConfig.save();
        rebuildWidgets();
    }

    private void load(int index) {
        if (index < 0 || index >= presets().size()) return;
        drawing.pixels = presets().get(index).pixels.clone();
        CrosshairConfig.save();
        onClose();
    }

    private void delete(int index) {
        if (pendingDelete != index) {          // first click asks "Sure?"
            pendingDelete = index;
            rebuildWidgets();
            return;
        }
        if (index >= 0 && index < presets().size()) presets().remove(index);
        pendingDelete = -1;
        CrosshairConfig.save();
        rebuildWidgets();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        ctx.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);

        List<SavedPreset> list = presets();
        if (list.isEmpty()) {
            ctx.centeredText(font, Component.literal("No saved crosshairs yet - name one and press Save current."),
                    width / 2, listTop + 6, 0xFFAAAAAA);
        }
        int start = page * perPage;
        for (int i = start; i < Math.min(list.size(), start + perPage); i++) {
            SavedPreset p = list.get(i);
            int ry = listTop + (i - start) * ROW;
            // small preview of the drawing
            int tx = left, ty = ry + 2;
            ctx.fill(tx - 1, ty - 1, tx + GRID + 1, ty + GRID + 1, 0xFF000000);
            ctx.fill(tx, ty, tx + GRID, ty + GRID, 0xFF333333);
            for (int cy = 0; cy < GRID; cy++) for (int cx = 0; cx < GRID; cx++) {
                if (p.pixels[cy * GRID + cx]) ctx.fill(tx + cx, ty + cy, tx + cx + 1, ty + cy + 1, 0xFFFFFFFF);
            }
            ctx.text(font, Component.literal(p.name), left + 22, ry + 6, 0xFFFFFFFF, true);
        }

        int pages = Math.max(1, (list.size() + perPage - 1) / perPage);
        ctx.centeredText(font, Component.literal((page + 1) + "/" + pages), left + 52, height - 20, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        CrosshairConfig.save();
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }
}
