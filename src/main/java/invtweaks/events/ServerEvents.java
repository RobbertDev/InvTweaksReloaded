package invtweaks.events;

import invtweaks.InvTweaksMod;
import invtweaks.config.InvTweaksConfig;
import invtweaks.util.ClientUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = InvTweaksMod.MODID)
public final class ServerEvents {
    private ServerEvents() {}

    private static final Map<Player, EnumMap<InteractionHand, Item>> itemsCache = new WeakHashMap<>();
    private static final Map<Player, Object2IntMap<Item>> usedCache = new WeakHashMap<>();

    /** Hotbar/main slots are 0-35; armor is 36-39; offhand is 40. We refuse to refill from armor slots. */
    private static final int ARMOR_START = 36;
    private static final int ARMOR_END_EXCLUSIVE = 40;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player ent = event.getEntity();
        if (!ent.level().isClientSide()) {
            if (!(ent instanceof ServerPlayer serverPlayer)) return;
            if (!InvTweaksConfig.getPlayerAutoRefill(ent)) return;

            EnumMap<InteractionHand, Item> cached = itemsCache.computeIfAbsent(ent, k -> new EnumMap<>(InteractionHand.class));
            Object2IntMap<Item> ucached = usedCache.computeIfAbsent(ent, k -> new Object2IntOpenHashMap<>());
            for (InteractionHand hand : InteractionHand.values()) {
                Item previous = cached.get(hand);
                if (previous != null
                        && ent.getItemInHand(hand).isEmpty()
                        && serverPlayer.getStats().getValue(Stats.ITEM_USED.get(previous))
                                > ucached.getOrDefault(previous, Integer.MAX_VALUE)) {
                    searchForSubstitute(ent, hand, previous);
                }
                ItemStack held = ent.getItemInHand(hand);
                cached.put(hand, held.isEmpty() ? null : held.getItem());
                if (!held.isEmpty()) {
                    ucached.put(held.getItem(), serverPlayer.getStats().getValue(Stats.ITEM_USED.get(held.getItem())));
                }
            }
        } else {
            // Client side: push config to server when dirty.
            if (InvTweaksConfig.isDirty()) {
                if (ClientUtils.serverConnectionExists()) {
                    ClientPacketDistributor.sendToServer(InvTweaksConfig.getSyncPacket());
                }
                InvTweaksConfig.setDirty(false);
            }
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() == ClientUtils.safeGetPlayer()) {
            InvTweaksConfig.setDirty(true);
        }
    }

    private static void searchForSubstitute(Player ent, InteractionHand hand, Item item) {
        IntList frozen = Optional.ofNullable(InvTweaksConfig.getPlayerRules(ent).catToInventorySlots("/FROZEN"))
                .map(IntArrayList::new)
                .orElseGet(IntArrayList::new);
        frozen.sort(null);

        Inventory inv = ent.getInventory();
        if (Collections.binarySearch(frozen, inv.getSelectedSlot()) >= 0) {
            return; // ignore frozen slot
        }

        TagKey<Item> altTag = altToolTag(item);

        int alternativeSlot = -1;
        int size = inv.getContainerSize();
        for (int i = 0; i < size; ++i) {
            if (Collections.binarySearch(frozen, i) >= 0) continue;
            if (i >= ARMOR_START && i < ARMOR_END_EXCLUSIVE) continue; // skip armor slots

            ItemStack candidate = inv.getItem(i);
            if (candidate.isEmpty()) continue;

            if (candidate.is(item)) {
                ItemStack moved = candidate.copy();
                inv.setItem(i, ItemStack.EMPTY);
                ent.setItemInHand(hand, moved);
                return;
            }
            if (altTag != null && candidate.is(altTag) && alternativeSlot < 0) {
                alternativeSlot = i;
            }
        }
        if (alternativeSlot >= 0 && ent.getItemInHand(hand).isEmpty()) {
            ItemStack moved = inv.getItem(alternativeSlot).copy();
            inv.setItem(alternativeSlot, ItemStack.EMPTY);
            ent.setItemInHand(hand, moved);
        }
    }

    /**
     * Map a broken tool to a tag we'll accept as a fallback substitute.
     * 26.1 removed the {@code SwordItem}/{@code PickaxeItem}/{@code TieredItem} hierarchy in favor of data components,
     * so we identify tools by tag membership of the broken stack itself.
     */
    private static TagKey<Item> altToolTag(Item item) {
        ItemStack probe = new ItemStack(item);
        if (probe.is(ItemTags.SWORDS)) return ItemTags.SWORDS;
        if (probe.is(ItemTags.PICKAXES)) return ItemTags.PICKAXES;
        if (probe.is(ItemTags.AXES)) return ItemTags.AXES;
        if (probe.is(ItemTags.SHOVELS)) return ItemTags.SHOVELS;
        if (probe.is(ItemTags.HOES)) return ItemTags.HOES;
        return null;
    }
}
