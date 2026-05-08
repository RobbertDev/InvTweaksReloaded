package invtweaks.gui;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.gui.widget.ExtendedButton;

/**
 * Vanilla-styled button used by the mod's sort UI. Default size matches the original 1.21.1
 * widget (14x16); a custom-size constructor lets the player-inventory row pack four smaller
 * buttons above the top-right slot.
 *
 * The 1.21.1 version blitted from {@code button_sprites.png} via {@code Gui#renderSlot},
 * but in 26.1 the {@code GuiGraphicsExtractor} pipeline replaced that path; we render text
 * to avoid coupling the port to an unstable blit signature.
 */
public class InvTweaksButton extends ExtendedButton {
    public InvTweaksButton(int x, int y, Component label, OnPress handler) {
        this(x, y, 14, 16, label, handler);
    }

    public InvTweaksButton(int x, int y, int width, int height, Component label, OnPress handler) {
        super(x, y, width, height, label, handler);
    }
}
