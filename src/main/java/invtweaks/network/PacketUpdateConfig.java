package invtweaks.network;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import invtweaks.InvTweaksMod;
import invtweaks.config.InvTweaksConfig;
import invtweaks.config.Ruleset;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record PacketUpdateConfig(
        List<UnmodifiableConfig> cats,
        List<String> rules,
        List<UnmodifiableConfig> contOverrides,
        boolean autoRefill
) implements CustomPacketPayload {
    public static final Type<PacketUpdateConfig> TYPE = new Type<>(Identifier.fromNamespaceAndPath(InvTweaksMod.MODID, "packet_update_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketUpdateConfig> CODEC = new StreamCodec<>() {
        @Override
        public PacketUpdateConfig decode(RegistryFriendlyByteBuf buf) {
            List<UnmodifiableConfig> cats = new ArrayList<>();
            int catsSize = buf.readVarInt();
            for (int i = 0; i < catsSize; ++i) {
                CommentedConfig subCfg = CommentedConfig.inMemory();
                subCfg.set("name", buf.readUtf(32767));
                List<String> spec = new ArrayList<>();
                int specSize = buf.readVarInt();
                for (int j = 0; j < specSize; ++j) {
                    spec.add(buf.readUtf(32767));
                }
                subCfg.set("spec", spec);
                cats.add(subCfg);
            }
            List<String> rules = new ArrayList<>();
            int rulesSize = buf.readVarInt();
            for (int i = 0; i < rulesSize; ++i) {
                rules.add(buf.readUtf(32767));
            }
            List<UnmodifiableConfig> contOverrides = new ArrayList<>();
            int contOverridesSize = buf.readVarInt();
            for (int i = 0; i < contOverridesSize; ++i) {
                CommentedConfig contOverride = CommentedConfig.inMemory();
                contOverride.set("containerClass", buf.readUtf(32767));
                contOverride.set("x", buf.readInt());
                contOverride.set("y", buf.readInt());
                contOverride.set("sortRange", buf.readUtf(32767));
                contOverrides.add(contOverride);
            }
            return new PacketUpdateConfig(cats, rules, contOverrides, buf.readBoolean());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, PacketUpdateConfig packet) {
            buf.writeVarInt(packet.cats.size());
            for (UnmodifiableConfig subCfg : packet.cats) {
                buf.writeUtf(subCfg.getOrElse("name", ""));
                List<String> spec = subCfg.getOrElse("spec", Collections.emptyList());
                buf.writeVarInt(spec.size());
                for (String subSpec : spec) {
                    buf.writeUtf(subSpec);
                }
            }
            buf.writeVarInt(packet.rules.size());
            for (String subRule : packet.rules) {
                buf.writeUtf(subRule);
            }
            buf.writeVarInt(packet.contOverrides.size());
            for (UnmodifiableConfig contOverride : packet.contOverrides) {
                buf.writeUtf(contOverride.getOrElse("containerClass", ""));
                int x = contOverride.getIntOrElse("x", InvTweaksConfig.NO_POS_OVERRIDE);
                int y = contOverride.getIntOrElse("y", InvTweaksConfig.NO_POS_OVERRIDE);
                buf.writeInt(x).writeInt(y);
                buf.writeUtf(contOverride.getOrElse("sortRange", InvTweaksConfig.NO_SPEC_OVERRIDE));
            }
            buf.writeBoolean(packet.autoRefill);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PacketUpdateConfig packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            InvTweaksConfig.setPlayerCats(ctx.player(), InvTweaksConfig.cfgToCompiledCats(packet.cats));
            InvTweaksConfig.setPlayerRules(ctx.player(), new Ruleset(packet.rules));
            InvTweaksConfig.setPlayerAutoRefill(ctx.player(), packet.autoRefill);
            InvTweaksConfig.setPlayerContOverrides(ctx.player(), InvTweaksConfig.cfgToCompiledContOverrides(packet.contOverrides));
        });
    }
}
