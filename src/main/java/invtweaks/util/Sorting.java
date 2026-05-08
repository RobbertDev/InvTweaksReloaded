package invtweaks.util;

import com.google.common.base.Equivalence;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Streams;
import invtweaks.InvTweaksMod;
import invtweaks.config.Category;
import invtweaks.config.ContOverride;
import invtweaks.config.InvTweaksConfig;
import invtweaks.config.Ruleset;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PrimitiveIterator;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class Sorting {
    /** First 36 slots of {@link Inventory} are the main inventory (hotbar + 3 rows). */
    private static final int PLAYER_MAIN_SIZE = 36;

    private Sorting() {}

    public static void executeSort(Player player, boolean isPlayerSort, String screenClass) {
        executeSort(player, isPlayerSort, screenClass, SortMode.DEFAULT, false);
    }

    public static void executeSort(Player player, boolean isPlayerSort, String screenClass, SortMode mode) {
        executeSort(player, isPlayerSort, screenClass, mode, false);
    }

    public static void executeSort(Player player, boolean isPlayerSort, String screenClass, SortMode mode, boolean reverse) {
        if (player != null && InvTweaksConfig.isDebugEnabled()) {
            InvTweaksMod.LOGGER.debug("screen: {} mode: {} reverse: {}", screenClass, mode, reverse);
        }
        if (isPlayerSort) {
            Map<String, Category> cats = InvTweaksConfig.getPlayerCats(player);
            Ruleset rules = InvTweaksConfig.getPlayerRules(player);
            IntList lockedSlots = Optional.ofNullable(rules.catToInventorySlots("/LOCKED"))
                    .<IntList>map(IntArrayList::new)
                    .orElseGet(IntArrayList::new);
            lockedSlots.addAll(Optional.ofNullable(rules.catToInventorySlots("/FROZEN")).orElse(IntLists.EMPTY_LIST));
            lockedSlots.sort(null);

            if (mode == SortMode.DEFAULT && !reverse) {
                // Categorized rules sort. Reverse mode skips categories and falls through to a flat reversed sort.
                if (player instanceof ServerPlayer serverPlayer) {
                    playerSortServer(serverPlayer, cats, rules, lockedSlots);
                } else {
                    playerSortClient(player, cats, rules, lockedSlots);
                }
            } else {
                List<Slot> validSlots = player.containerMenu.slots.stream()
                        .filter(slot -> slot.container instanceof Inventory)
                        .filter(slot -> 0 <= slot.getSlotIndex() && slot.getSlotIndex() < PLAYER_MAIN_SIZE)
                        .filter(slot -> Collections.binarySearch(lockedSlots, slot.getSlotIndex()) < 0)
                        .filter(slot -> slot.mayPickup(player) && (slot.mayPlace(slot.getItem()) || !slot.hasItem()))
                        .collect(Collectors.toCollection(ArrayList::new));
                if (player instanceof ServerPlayer serverPlayer) {
                    flatSortServer(serverPlayer, validSlots, mode.comparator(reverse));
                } else {
                    flatSortClient(player, validSlots, mode.comparator(reverse));
                }
            }
        } else {
            AbstractContainerMenu cont = player.containerMenu;
            if (cont == player.inventoryMenu) return;

            ContOverride override = InvTweaksConfig.getPlayerContOverride(player, screenClass, cont.getClass().getName());
            if (override != null && override.isSortDisabled()) return;

            List<Slot> validSlots =
                    (override != null && override.getSortRange() != null
                            ? override.getSortRange().intStream()
                                    .filter(idx -> 0 <= idx && idx < cont.slots.size())
                                    .mapToObj(cont.slots::get)
                            : cont.slots.stream())
                            .filter(slot -> slot.container.getContainerSize() > 0 && !(slot.container instanceof Inventory))
                            .filter(slot -> slot.mayPickup(player) && (slot.mayPlace(slot.getItem()) || !slot.hasItem()))
                            .collect(Collectors.toCollection(ArrayList::new));

            if (player instanceof ServerPlayer serverPlayer) {
                flatSortServer(serverPlayer, validSlots, mode.comparator(reverse));
            } else {
                flatSortClient(player, validSlots, mode.comparator(reverse));
            }
        }
    }

    private static void playerSortClient(Player player, Map<String, Category> cats, Ruleset rules, IntList lockedSlots) {
        Inventory inv = player.getInventory();
        MultiPlayerGameMode pc = Minecraft.getInstance().gameMode;
        if (pc == null) return;

        Int2ObjectMap<Slot> indexToSlot = player.containerMenu.slots.stream()
                .filter(slot -> slot.container instanceof Inventory)
                .filter(slot -> 0 <= slot.getSlotIndex() && slot.getSlotIndex() < PLAYER_MAIN_SIZE)
                .collect(Collectors.toMap(
                        Slot::getSlotIndex,
                        Function.identity(),
                        (u, v) -> u,
                        Int2ObjectOpenHashMap::new));

        IntList stackIdxs = IntStream.range(0, PLAYER_MAIN_SIZE)
                .filter(idx -> Collections.binarySearch(lockedSlots, idx) < 0)
                .filter(idx -> !inv.getItem(idx).isEmpty())
                .collect(IntArrayList::new, IntList::add, IntList::addAll);

        Map<Equivalence.Wrapper<ItemStack>, Set<Slot>> gatheredSlots =
                Utils.gatheredSlots(() -> stackIdxs.stream()
                        .mapToInt(v -> v)
                        .mapToObj(indexToSlot::get)
                        .filter(Objects::nonNull)
                        .filter(Slot::hasItem)
                        .iterator());
        List<Equivalence.Wrapper<ItemStack>> stackWs = new ArrayList<>(gatheredSlots.keySet());
        stackWs.sort(Comparator.comparing(Equivalence.Wrapper::get, Utils.FALLBACK_COMPARATOR));

        for (Map.Entry<String, Category> ent : cats.entrySet()) {
            IntList specificRules = rules.catToInventorySlots(ent.getKey());
            if (specificRules == null) specificRules = IntLists.EMPTY_LIST;
            specificRules = specificRules.stream()
                    .filter(idx -> Collections.binarySearch(lockedSlots, idx) < 0)
                    .mapToInt(v -> v)
                    .collect(IntArrayList::new, IntList::add, IntList::addAll);

            List<Slot> specificRulesSlots = specificRules.stream()
                    .map(idx -> indexToSlot.get((int) idx))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(ArrayList::new));
            ListIterator<Slot> toIt = specificRulesSlots.listIterator();

            Client.processCategoryClient(player, pc, gatheredSlots, stackWs, ent.getValue(), toIt);
        }

        List<Slot> fallbackList = Stream.concat(
                        Streams.stream(Optional.ofNullable(rules.catToInventorySlots("/OTHER"))).flatMap(List::stream),
                        rules.fallbackInventoryRules().stream())
                .mapToInt(v -> v)
                .filter(idx -> Collections.binarySearch(lockedSlots, idx) < 0)
                .distinct()
                .mapToObj(indexToSlot::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));

        Client.processCategoryClient(player, pc, gatheredSlots, stackWs, null, fallbackList.listIterator());
    }

    private static void playerSortServer(ServerPlayer player, Map<String, Category> cats, Ruleset rules, IntList lockedSlots) {
        Inventory inv = player.getInventory();

        List<ItemStack> stacks = Utils.condensed(() ->
                IntStream.range(0, PLAYER_MAIN_SIZE)
                        .filter(idx -> Collections.binarySearch(lockedSlots, idx) < 0)
                        .mapToObj(inv::getItem)
                        .filter(st -> !st.isEmpty())
                        .iterator());
        stacks.sort(Utils.FALLBACK_COMPARATOR);
        stacks = new LinkedList<>(stacks);

        for (int i = 0; i < PLAYER_MAIN_SIZE; ++i) {
            if (Collections.binarySearch(lockedSlots, i) < 0) {
                inv.setItem(i, ItemStack.EMPTY);
            }
        }

        for (Map.Entry<String, Category> ent : cats.entrySet()) {
            IntList specificRules = rules.catToInventorySlots(ent.getKey());
            if (specificRules == null) specificRules = IntLists.EMPTY_LIST;
            specificRules = specificRules.stream()
                    .filter(idx -> Collections.binarySearch(lockedSlots, idx) < 0)
                    .mapToInt(v -> v)
                    .collect(IntArrayList::new, IntList::add, IntList::addAll);
            List<ItemStack> curStacks = new ArrayList<>();
            Iterator<ItemStack> it = stacks.iterator();
            while (it.hasNext() && curStacks.size() < specificRules.size()) {
                ItemStack st = it.next();
                if (ent.getValue().checkStack(st) >= 0) {
                    curStacks.add(st);
                    it.remove();
                }
            }
            curStacks.sort(Comparator.comparingInt(s -> ent.getValue().checkStack(s)));
            //noinspection UnstableApiUsage
            Streams.zip(specificRules.stream(), curStacks.stream(), Pair::of)
                    .forEach(pr -> inv.setItem(pr.getKey(), pr.getValue()));
        }

        PrimitiveIterator.OfInt fallbackIt = Stream.concat(
                        Optional.ofNullable(rules.catToInventorySlots("/OTHER")).stream().flatMap(List::stream),
                        rules.fallbackInventoryRules().stream())
                .mapToInt(v -> v)
                .iterator();
        while (fallbackIt.hasNext()) {
            int idx = fallbackIt.nextInt();
            if (Collections.binarySearch(lockedSlots, idx) >= 0) continue;
            if (stacks.isEmpty()) break;
            if (inv.getItem(idx).isEmpty()) {
                inv.setItem(idx, stacks.remove(0));
            }
        }
    }

    private static void flatSortClient(Player player, List<Slot> validSlots, Comparator<ItemStack> comparator) {
        MultiPlayerGameMode pc = Minecraft.getInstance().gameMode;
        if (pc == null) return;

        Map<Equivalence.Wrapper<ItemStack>, Set<Slot>> gatheredSlots =
                Utils.gatheredSlots(() -> validSlots.stream().filter(Slot::hasItem).iterator());
        List<Equivalence.Wrapper<ItemStack>> stackWs = new ArrayList<>(gatheredSlots.keySet());
        stackWs.sort(Comparator.comparing(Equivalence.Wrapper::get, comparator));

        ListIterator<Slot> toIt = validSlots.listIterator();
        for (Equivalence.Wrapper<ItemStack> stackW : stackWs) {
            BiMap<Slot, Slot> displaced = HashBiMap.create();
            Client.clientPushToSlots(player, pc, gatheredSlots.get(stackW).iterator(), toIt, displaced);
            for (Map.Entry<Slot, Slot> displacedPair : displaced.entrySet()) {
                Set<Slot> toModify = gatheredSlots.get(Utils.STACKABLE.wrap(displacedPair.getValue().getItem()));
                if (toModify != null) {
                    toModify.remove(displacedPair.getKey());
                    toModify.add(displacedPair.getValue());
                }
            }
        }
    }

    private static void flatSortServer(ServerPlayer serverPlayer, List<Slot> validSlots, Comparator<ItemStack> comparator) {
        if (validSlots.isEmpty()) return;
        List<ItemStack> stacks = Utils.condensed(() -> validSlots.stream()
                .map(Slot::getItem)
                .filter(st -> !st.isEmpty())
                .iterator());
        stacks.sort(comparator);

        Iterator<Slot> slotIt = validSlots.iterator();
        for (ItemStack stack : stacks) {
            Slot cur = null;
            while (slotIt.hasNext() && !(cur = slotIt.next()).mayPlace(stack)) {
                // skip
            }
            if (cur == null || !cur.mayPlace(stack)) {
                return; // bail without mutating
            }
        }

        validSlots.forEach(slot -> slot.set(ItemStack.EMPTY));
        slotIt = validSlots.iterator();
        for (ItemStack stack : stacks) {
            Slot cur = null;
            while (slotIt.hasNext() && !(cur = slotIt.next()).mayPlace(stack)) {
                // skip
            }
            if (cur != null) cur.set(stack);
        }
    }

    /** Extracted to keep client-only API references off the server class load path. */
    private static final class Client {
        private Client() {}

        static void processCategoryClient(
                Player player,
                MultiPlayerGameMode pc,
                Map<Equivalence.Wrapper<ItemStack>, Set<Slot>> gatheredSlots,
                List<Equivalence.Wrapper<ItemStack>> stackWs,
                Category cat,
                ListIterator<Slot> toIt) {
            List<Equivalence.Wrapper<ItemStack>> subStackWs = cat == null
                    ? new ArrayList<>(stackWs)
                    : stackWs.stream()
                            .filter(stackW -> cat.checkStack(stackW.get()) >= 0)
                            .sorted(Comparator.comparingInt(stackW -> cat.checkStack(stackW.get())))
                            .collect(Collectors.toCollection(ArrayList::new));

            for (Equivalence.Wrapper<ItemStack> stackW : subStackWs) {
                if (cat == null || cat.checkStack(stackW.get()) >= 0) {
                    BiMap<Slot, Slot> displaced = HashBiMap.create();
                    Iterator<Slot> fromIt = gatheredSlots.get(stackW).iterator();
                    Client.clientPushToSlots(player, pc, fromIt, toIt, displaced);
                    for (Map.Entry<Slot, Slot> displacedPair : displaced.entrySet()) {
                        Equivalence.Wrapper<ItemStack> displacedW = Utils.STACKABLE.wrap(displacedPair.getValue().getItem());
                        Set<Slot> toModify = gatheredSlots.get(displacedW);
                        if (toModify != null) {
                            toModify.remove(displacedPair.getKey());
                            toModify.add(displacedPair.getValue());
                        }
                    }
                }
            }
            stackWs.removeIf(sw -> gatheredSlots.get(sw).isEmpty());
            gatheredSlots.values().removeIf(Set::isEmpty);
        }

        /**
         * Moves items from {@code originIter}'s slots into {@code destIter}'s slots by simulating clicks.
         * Records any displaced items in {@code displaced} so callers can update their slot bookkeeping.
         */
        static boolean clientPushToSlots(Player player, MultiPlayerGameMode playerController,
                                         Iterator<Slot> originIter, ListIterator<Slot> destIter,
                                         BiMap<Slot, Slot> displaced) {
            if (!destIter.hasNext()) return true;

            boolean completedCurrentItemSwap = true;

            while (originIter.hasNext()) {
                completedCurrentItemSwap = false;
                Slot originSlot = originIter.next();
                playerController.handleContainerInput(player.containerMenu.containerId, originSlot.index, 0, ContainerInput.PICKUP, player);

                Slot destinationSlot = null;
                while (destIter.hasNext()) {
                    if (destIter.hasPrevious()) {
                        destinationSlot = destIter.previous();
                        if (destinationSlot.getItem().getCount() != Math.min(destinationSlot.getMaxStackSize(), destinationSlot.getItem().getMaxStackSize())
                                && Utils.STACKABLE.equivalent(destinationSlot.getItem(), player.containerMenu.getCarried())) {
                            // stay on the previous slot — fall through to the destIter.next() below to land back on it
                        } else {
                            destIter.next();
                        }
                    }

                    destinationSlot = destIter.next();
                    playerController.handleContainerInput(player.containerMenu.containerId, destinationSlot.index, 0, ContainerInput.PICKUP, player);

                    if (player.containerMenu.getCarried().isEmpty()) {
                        completedCurrentItemSwap = true;
                        break;
                    } else {
                        if (Utils.STACKABLE.equivalent(destinationSlot.getItem(), player.containerMenu.getCarried())) continue;
                        playerController.handleContainerInput(player.containerMenu.containerId, originSlot.index, 0, ContainerInput.PICKUP, player);

                        if (originSlot.hasItem() && !ItemStack.isSameItemSameComponents(originSlot.getItem(), destinationSlot.getItem())) {
                            completedCurrentItemSwap = true;
                            displaced.put(destinationSlot, originSlot);
                            break;
                        }
                    }
                }
                if (!destIter.hasNext() && Optional.ofNullable(destinationSlot)
                        .filter(s -> s.getItem().getCount() >= Math.min(s.getMaxStackSize(), s.getItem().getMaxStackSize()))
                        .isPresent()) {
                    break;
                }
            }
            return completedCurrentItemSwap;
        }
    }
}
