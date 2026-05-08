package invtweaks.network;

import invtweaks.InvTweaksMod;
import invtweaks.util.SortMode;
import invtweaks.util.Sorting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PacketSortInv(boolean isPlayer, String screenName, int sortMode, boolean reverse) implements CustomPacketPayload {
    public static final Type<PacketSortInv> TYPE = new Type<>(Identifier.fromNamespaceAndPath(InvTweaksMod.MODID, "packet_sort_inv"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketSortInv> CODEC = new StreamCodec<>() {
        @Override
        public PacketSortInv decode(RegistryFriendlyByteBuf buf) {
            return new PacketSortInv(buf.readBoolean(), buf.readUtf(), buf.readByte(), buf.readBoolean());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, PacketSortInv payload) {
            buf.writeBoolean(payload.isPlayer);
            buf.writeUtf(payload.screenName);
            buf.writeByte(payload.sortMode);
            buf.writeBoolean(payload.reverse);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PacketSortInv payload, IPayloadContext context) {
        SortMode mode = SortMode.fromOrdinal(payload.sortMode);
        context.enqueueWork(() -> Sorting.executeSort(context.player(), payload.isPlayer, payload.screenName, mode, payload.reverse))
                .exceptionally(e -> {
                    InvTweaksMod.LOGGER.error("Failed to sort inventory", e);
                    return null;
                });
    }
}
