package invtweaks.events;

import com.mojang.blaze3d.platform.InputConstants;
import invtweaks.InvTweaksMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = InvTweaksMod.MODID, value = Dist.CLIENT)
public final class KeyMappings {
    private KeyMappings() {}

    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(InvTweaksMod.MODID, "invtweaks"));

    public static final KeyMapping SORT_PLAYER = new KeyMapping(
            "key.invtweaks_sort_player.desc",
            KeyConflictContext.GUI, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_BACKSLASH, CATEGORY);

    public static final KeyMapping SORT_INVENTORY = new KeyMapping(
            "key.invtweaks_sort_inventory.desc",
            KeyConflictContext.GUI, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_GRAVE_ACCENT, CATEGORY);

    public static final KeyMapping SORT_EITHER = new KeyMapping(
            "key.invtweaks_sort_either.desc",
            KeyConflictContext.GUI, InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_MIDDLE, CATEGORY);

    @SubscribeEvent
    public static void registerKeyMappings(final RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(SORT_PLAYER);
        event.register(SORT_INVENTORY);
        event.register(SORT_EITHER);
    }
}
