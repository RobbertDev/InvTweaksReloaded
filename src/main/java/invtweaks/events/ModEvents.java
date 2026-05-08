package invtweaks.events;

import invtweaks.InvTweaksMod;
import invtweaks.config.ContOverride;
import invtweaks.config.InvTweaksConfig;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.InterModProcessEvent;

@EventBusSubscriber(modid = InvTweaksMod.MODID)
public final class ModEvents {
    private ModEvents() {}

    @SubscribeEvent
    public static void interModProcess(InterModProcessEvent event) {
        event.getIMCStream().forEach(imcMessage -> {
            if (imcMessage.modId().equals(InvTweaksMod.MODID)
                    && imcMessage.method().equals(InvTweaksMod.IMS_METHOD_BLACKLIST)) {
                String pattern = imcMessage.messageSupplier().get().toString();
                InvTweaksMod.LOGGER.debug("adding blacklist from {} for {}", imcMessage.senderModId(), pattern);
                InvTweaksConfig.IMS_CONT_OVERRIDES.put(
                        pattern,
                        new ContOverride(InvTweaksConfig.NO_POS_OVERRIDE, InvTweaksConfig.NO_POS_OVERRIDE, ""));
            }
        });
    }
}
