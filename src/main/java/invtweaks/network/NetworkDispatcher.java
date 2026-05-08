package invtweaks.network;

import invtweaks.InvTweaksMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = InvTweaksMod.MODID)
public final class NetworkDispatcher {
    private NetworkDispatcher() {}

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(InvTweaksMod.MODID);

        registrar.optional()
                .playToServer(PacketSortInv.TYPE, PacketSortInv.CODEC, PacketSortInv::handle)
                .playToServer(PacketUpdateConfig.TYPE, PacketUpdateConfig.CODEC, PacketUpdateConfig::handle);
    }
}
