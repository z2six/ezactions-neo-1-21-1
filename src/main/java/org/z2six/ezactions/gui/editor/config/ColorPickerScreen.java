// MainFile: src/main/java/org/z2six/ezactions/gui/editor/config/ColorPickerScreen.java
package org.z2six.ezactions.gui.editor.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.z2six.ezactions.Constants;

import java.util.function.Consumer;

/**
 * // MainFile: src/main/java/org/z2six/ezactions/gui/editor/config/ColorPickerScreen.java
 * Simple HSV + Alpha color picker:
 * - Left: Sat/Val square for current hue
 * - Right: vertical Hue slider
 * - Below: Alpha slider
 * - Hex input box + live preview
 *
 * Returns ARGB via onPick.accept(color) when "OK" is pressed.
 */
public final class ColorPickerScreen extends Screen {

    private final Screen parent;
    private final Consumer<Integer> onPick;

    // HSV + A draft
    private float h;    // 0..1
    private float s;    // 0..1
    private float v;    // 0..1
    private int   a;    // 0..255

    // Widgets
    private EditBox hexBox;

    // Geometry
    private int pad = 12;
    private int svX, svY, svW, svH;
    private int hueX, hueY, hueW, hueH;
    private int aX, aY, aW, aH;

    private boolean draggingSV = false;
    private boolean draggingHue = false;
    private boolean draggingA = false;

    public ColorPickerScreen(Screen parent, int initialARGB, Consumer<Integer> onPick) {
        super(Component.literal("Pick Color"));
        this.parent = parent;
        this.onPick = onPick;

        int R = ColorUtil.r(initialARGB);
        int G = ColorUtil.g(initialARGB);
        int B = ColorUtil.b(initialARGB);
        this.a = ColorUtil.a(initialARGB);
        float[] hsv = ColorUtil.rgbToHsv(R, G, B);
        this.h = hsv[0];
        this.s = hsv[1];
        this.v = hsv[2];
    }

    @Override
    protected void init() {
        int cx = this.width / 2;

        // Layout
        svW = Math.min(220, Math.max(140, this.width - 2*pad - 60));
        svH = svW; // square
        svX = pad;
        svY = pad + 20;

        hueW = 16;
        hueH = svH;
        hueX = svX + svW + 10;
        hueY = svY;

        aW = svW + hueW + 10;
        aH = 12;
        aX = svX;
        aY = svY + svH + 12;

        // Hex box + buttons
        hexBox = new EditBox(this.font, svX, aY + aH + 10, aW - 180, 20, Component.literal("ARGB"));
        hexBox.setValue(ColorUtil.toHexARGB(current()));
        hexBox.setResponder(s -> {
            int parsed = ColorUtil.parseHexARGB(s, current());
            // Only replace draft if parse looks sane (keep A)
            this.a = ColorUtil.a(parsed);
            float[] hsv = ColorUtil.rgbToHsv(ColorUtil.r(parsed), ColorUtil.g(parsed), ColorUtil.b(parsed));
            this.h = hsv[0]; this.s = hsv[1]; this.v = hsv[2];
        });
        addRenderableWidget(hexBox);

        int btnY = hexBox.getY();
        int btnX = hexBox.getX() + hexBox.getWidth() + 6;

        addRenderableWidget(Button.builder(Component.literal("OK"), b -> {
            try {
                if (onPick != null) onPick.accept(current());
            } catch (Throwable t) {
                Constants.LOG.warn("[{}] ColorPicker OK failed: {}", Constants.MOD_NAME, t.toString());
            }
            onClose();
        }).bounds(btnX, btnY, 80, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(btnX + 86, btnY, 80, 20).build());
    }

    private int current() {
        int[] rgb = ColorUtil.hsvToRgb(h, s, v);
        return ColorUtil.argb(a, rgb[0], rgb[1], rgb[2]);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xA0000000);
        g.drawString(this.font, "Saturation / Value", svX, svY - 12, 0xA0A0A0);
        g.drawString(this.font, "Hue", hueX, hueY - 12, 0xA0A0A0);
        g.drawString(this.font, "Alpha", aX, aY - 12, 0xA0A0A0);

        // SV square (for current hue)
        int[] hueRgb = ColorUtil.hsvToRgb(h, 1f, 1f);
        // We render SV with simple gradients: X = S, Y = (1-V)
        for (int y = 0; y < svH; y++) {
            float vRow = 1f - (y / (float)(svH - 1));
            for (int x = 0; x < svW; x++) {
                float sCol = x / (float)(svW - 1);
                int[] rgb = ColorUtil.hsvToRgb(h, sCol, vRow);
                int col = ColorUtil.argb(255, rgb[0], rgb[1], rgb[2]);
                g.fill(svX + x, svY + y, svX + x + 1, svY + y + 1, col);
            }
        }

        // Hue bar (vertical rainbow)
        for (int y = 0; y < hueH; y++) {
            float hh = y / (float)(hueH - 1); // 0..1
            int[] rgb = ColorUtil.hsvToRgb(hh, 1f, 1f);
            int col = ColorUtil.argb(255, rgb[0], rgb[1], rgb[2]);
            g.fill(hueX, hueY + y, hueX + hueW, hueY + y + 1, col);
        }

        // Alpha bar (checker + gradient)
        // Checker background
        int cell = 6;
        for (int yy = 0; yy < aH; yy += cell) {
            for (int xx = 0; xx < aW; xx += cell) {
                int c = (((xx / cell) + (yy / cell)) % 2 == 0) ? 0xFFCCCCCC : 0xFFFFFFFF;
                g.fill(aX + xx, aY + yy, aX + Math.min(xx + cell, aX + aW), aY + Math.min(yy + cell, aY + aH), c);
            }
        }
        int[] rgb = ColorUtil.hsvToRgb(h, s, v);
        for (int x = 0; x < aW; x++) {
            float t = x / (float)(aW - 1);
            int aa = Math.round(t * 255f);
            int col = ColorUtil.argb(aa, rgb[0], rgb[1], rgb[2]);
            g.fill(aX + x, aY, aX + x + 1, aY + aH, col);
        }

        // Cursors
        int svCX = svX + Math.round(s * (svW - 1));
        int svCY = svY + Math.round((1f - v) * (svH - 1));
        drawCursor(g, svCX, svCY);

        int hueCY = hueY + Math.round(h * (hueH - 1));
        g.fill(hueX - 2, hueCY - 1, hueX + hueW + 2, hueCY + 2, 0xFF000000);
        g.fill(hueX - 1, hueCY,     hueX + hueW + 1, hueCY + 1, 0xFFFFFFFF);

        int aCX = aX + Math.round((a / 255f) * (aW - 1));
        g.fill(aCX - 1, aY - 2, aCX + 2, aY + aH + 2, 0xFF000000);
        g.fill(aCX,     aY - 1, aCX + 1, aY + aH + 1, 0xFFFFFFFF);

        // Preview swatch + hex
        int previewX = hexBox != null ? hexBox.getX() + hexBox.getWidth() + 6 : svX;
        int previewY = hexBox != null ? hexBox.getY() - 24 : aY + aH + 10;
        int sw = 60, sh = 18;
        // checker bg
        int px = previewX, py = previewY;
        for (int yy = 0; yy < sh; yy += 6) {
            for (int xx = 0; xx < sw; xx += 6) {
                int c = (((xx / 6) + (yy / 6)) % 2 == 0) ? 0xFFCCCCCC : 0xFFFFFFFF;
                g.fill(px + xx, py + yy, px + Math.min(xx + 6, sw), py + Math.min(yy + 6, sh), c);
            }
        }
        g.fill(px, py, px + sw, py + sh, current());

        if (hexBox != null) {
            String hex = ColorUtil.toHexARGB(current());
            g.drawString(this.font, hex, px + sw + 6, py + 4, 0xFFFFFF);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawCursor(GuiGraphics g, int x, int y) {
        int s = 4;
        g.fill(x - s, y, x + s + 1, y + 1, 0xFFFFFFFF);
        g.fill(x, y - s, x + 1, y + s + 1, 0xFFFFFFFF);
        g.fill(x - s, y - 1, x + s + 1, y, 0xFF000000);
        g.fill(x - 1, y - s, x, y + s + 1, 0xFF000000);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);

        if (mx >= svX && mx < svX + svW && my >= svY && my < svY + svH) {
            updateSV(mx, my);
            draggingSV = true;
            syncHex();
            return true;
        }
        if (mx >= hueX && mx < hueX + hueW && my >= hueY && my < hueY + hueH) {
            updateHue(my);
            draggingHue = true;
            syncHex();
            return true;
        }
        if (mx >= aX && mx < aX + aW && my >= aY && my < aY + aH) {
            updateA(mx);
            draggingA = true;
            syncHex();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button != 0) return super.mouseDragged(mx, my, button, dx, dy);
        if (draggingSV) { updateSV(mx, my); syncHex(); return true; }
        if (draggingHue){ updateHue(my);    syncHex(); return true; }
        if (draggingA)  { updateA(mx);      syncHex(); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0) {
            draggingSV = draggingHue = draggingA = false;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    private void updateSV(double mx, double my) {
        float nx = (float)((mx - svX) / (double)(svW - 1));
        float ny = (float)((my - svY) / (double)(svH - 1));
        this.s = ColorUtil.clamp01(nx);
        this.v = ColorUtil.clamp01(1f - ny);
    }

    private void updateHue(double my) {
        float ny = (float)((my - hueY) / (double)(hueH - 1));
        this.h = Math.max(0f, Math.min(1f, ny));
    }

    private void updateA(double mx) {
        float nx = (float)((mx - aX) / (double)(aW - 1));
        this.a = Math.max(0, Math.min(255, Math.round(nx * 255f)));
    }

    private void syncHex() {
        if (hexBox != null) hexBox.setValue(ColorUtil.toHexARGB(current()));
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
