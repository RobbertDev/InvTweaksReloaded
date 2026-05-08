package invtweaks.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

/**
 * Sort button whose screen-space position is recomputed live from a slot anchor every frame,
 * whose label is rendered centered without the default margin so icon glyphs sit fully
 * inside the button (vanilla {@code extractDefaultLabel} reserves 2px on each side and
 * routes wider glyphs through a scrolling pipeline that hides them in tight buttons),
 * and which forwards left- and right-click separately to a {@link Handler} so right-click
 * can trigger a reversed sort.
 *
 * Why anchored: container screens may relayout after construction (e.g. {@code InventoryScreen}
 * shifts {@code leftPos} when the recipe book opens). Binding to a slot keeps the button glued
 * as the GUI moves.
 */
public class InvTweaksAnchoredButton extends InvTweaksButton {
    @FunctionalInterface
    public interface Handler {
        void onClick(boolean reverse);
    }

    /** Glyph height of the bundled sort-icon font; matches "height" in sort_icons.json. */
    private static final int ICON_GLYPH_HEIGHT = 8;

    private final AbstractContainerScreen<?> screen;
    private final Slot anchor;
    private final int dx;
    private final int dy;
    private final Handler handler;

    public InvTweaksAnchoredButton(AbstractContainerScreen<?> screen, Slot anchor, int dx, int dy,
                                   int width, int height, Component label, Handler handler) {
        super(0, 0, width, height, label, b -> {});
        this.screen = screen;
        this.anchor = anchor;
        this.dx = dx;
        this.dy = dy;
        this.handler = handler;
    }

    @Override
    public int getX() {
        return screen.getLeftPos() + anchor.x + dx;
    }

    @Override
    public int getY() {
        return screen.getTopPos() + anchor.y + dy;
    }

    @Override
    protected boolean isValidClickButton(MouseButtonInfo info) {
        return info.button() == 0 || info.button() == 1;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        handler.onClick(event.button() == 1);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractDefaultSprite(graphics);
        Font font = Minecraft.getInstance().font;
        Component label = getMessage();
        int labelWidth = font.width(label);
        int x = getX() + (getWidth() - labelWidth) / 2;
        int y = getY() + (getHeight() - ICON_GLYPH_HEIGHT) / 2;
        graphics.text(font, label, x, y, 0xFFFFFFFF, true);
    }
}
