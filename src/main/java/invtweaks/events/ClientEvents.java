package invtweaks.events;

import com.google.common.base.Throwables;
import com.mojang.blaze3d.platform.InputConstants;
import invtweaks.InvTweaksMod;
import invtweaks.config.ContOverride;
import invtweaks.config.InvTweaksConfig;
import invtweaks.config.Ruleset;
import invtweaks.gui.InvTweaksAnchoredButton;
import invtweaks.network.PacketSortInv;
import invtweaks.util.ClientUtils;
import invtweaks.util.SortMode;
import invtweaks.util.Sorting;
import invtweaks.util.Utils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import org.jspecify.annotations.Nullable;

@EventBusSubscriber(modid = InvTweaksMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    public static final int MIN_SLOTS = 9;

    private static void requestSort(boolean isPlayer, String screenClass) {
        requestSort(isPlayer, screenClass, SortMode.DEFAULT, false);
    }

    private static void requestSort(boolean isPlayer, String screenClass, SortMode mode) {
        requestSort(isPlayer, screenClass, mode, false);
    }

    private static void requestSort(boolean isPlayer, String screenClass, SortMode mode, boolean reverse) {
        if (ClientUtils.serverConnectionExists()) {
            ClientPacketDistributor.sendToServer(new PacketSortInv(isPlayer, screenClass, mode.ordinal(), reverse));
        } else {
            Sorting.executeSort(ClientUtils.safeGetPlayer(), isPlayer, screenClass, mode, reverse);
        }
        playSortSound();
    }

    private static void playSortSound() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.ITEM_PICKUP, 1.6f));
    }

    private static @Nullable Slot getDefaultButtonPlacement(Collection<Slot> slots, Predicate<Slot> filter) {
        if (slots.stream().filter(filter).count() < MIN_SLOTS) {
            return null;
        }
        return slots.stream()
                .filter(filter)
                .max(Comparator.<Slot>comparingInt(s -> s.x).thenComparingInt(s -> -s.y))
                .orElse(null);
    }

    private static final Set<Screen> screensWithExtSort = Collections.newSetFromMap(new WeakHashMap<>());

    /** Sort-mode buttons stacked vertically just to the right of the player inventory's top row. */
    private static final int PLAYER_BTN_W = 12;
    private static final int PLAYER_BTN_H = 12;
    private static final int PLAYER_BTN_GAP = 1;

    /** Custom bitmap font shipping three sort icons mapped to private-use code points. */
    private static final FontDescription SORT_ICON_FONT = new FontDescription.Resource(
            Identifier.fromNamespaceAndPath(InvTweaksMod.MODID, "sort_icons"));
    private static final Style SORT_ICON_STYLE = Style.EMPTY.withFont(SORT_ICON_FONT);

    private static void addPlayerSortButtons(ScreenEvent.Init.Post event, AbstractContainerScreen<?> screen, Slot anchor) {
        SortMode[] modes = { SortMode.DEFAULT, SortMode.NAME, SortMode.COUNT };
        // PUA code points point at glyphs in the bundled `sort_icons` bitmap font.
        String[] glyphs = { "", "", "" };
        String[] tooltipKeys = {
                "invtweaks.sort.default",
                "invtweaks.sort.name",
                "invtweaks.sort.count",
        };
        // Column offset 5px to the right of the top-right slot; rows stack downward
        // alongside the three rows of the main inventory.
        int dx = 21;

        for (int i = 0; i < modes.length; i++) {
            final SortMode mode = modes[i];
            int dy = i * (PLAYER_BTN_H + PLAYER_BTN_GAP);
            InvTweaksAnchoredButton btn = new InvTweaksAnchoredButton(
                    screen, anchor, dx, dy, PLAYER_BTN_W, PLAYER_BTN_H,
                    Component.literal(glyphs[i]).withStyle(SORT_ICON_STYLE),
                    reverse -> requestSort(true, screen.getClass().getName(), mode, reverse));
            btn.setTooltip(Tooltip.create(Component.translatable(tooltipKeys[i])));
            event.addListener(btn);
        }
    }

    @SubscribeEvent
    public static void onScreenEventInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        if (screen instanceof CreativeModeInventoryScreen) return;
        InvTweaksMod.LOGGER.info("[InvTweaks] screen init: {}", screen.getClass().getName());

        // Player inventory: row of 4 small buttons above the top-right of the top row.
        // Each picks a different sort mode. The buttons anchor to the slot so they follow
        // GUI relayouts (e.g. recipe-book toggle shifting leftPos).
        Slot placement = getDefaultButtonPlacement(screen.getMenu().slots, slot -> slot.container instanceof Inventory);
        if (placement != null
                && InvTweaksConfig.isSortEnabled(true)
                && InvTweaksConfig.isButtonEnabled(true)) {
            try {
                addPlayerSortButtons(event, screen, placement);
            } catch (Exception e) {
                Throwables.throwIfUnchecked(e);
                throw new RuntimeException(e);
            }
        }

        // External container button.
        ContOverride override = InvTweaksConfig.getPlayerContOverride(
                Minecraft.getInstance().player,
                screen.getClass().getName(),
                screen.getMenu().getClass().getName());
        boolean isSortDisabled = Optional.ofNullable(override).filter(ContOverride::isSortDisabled).isPresent();

        if (!isSortDisabled) {
            // Player-inventory screens with no external slots short-circuit via the placement-null
            // check below; the old EffectRenderingInventoryScreen exclusion is no longer needed
            // since 26.1 collapsed that hierarchy into a helper used by InventoryScreen.
            int x = InvTweaksConfig.NO_POS_OVERRIDE;
            int y = InvTweaksConfig.NO_POS_OVERRIDE;
            if (override != null) {
                x = override.getX();
                y = override.getY();
            }
            placement = getDefaultButtonPlacement(
                    screen.getMenu().slots,
                    slot -> !(slot.container instanceof Inventory || slot.container instanceof CraftingContainer));
            if (placement != null) {
                if (x == InvTweaksConfig.NO_POS_OVERRIDE) x = placement.x + 17;
                if (y == InvTweaksConfig.NO_POS_OVERRIDE) y = placement.y;
            }

            if (InvTweaksConfig.isSortEnabled(false) && placement != null) {
                try {
                    if (InvTweaksConfig.isButtonEnabled(false)) {
                        // Anchor to the placement slot so the button follows screen relayouts;
                        // dx/dy are the offsets that the override (or default placement) computed.
                        int dx = x - placement.x;
                        int dy = y - placement.y;
                        event.addListener(new InvTweaksAnchoredButton(
                                screen, placement, dx, dy, 14, 16,
                                Component.literal("⇵"),
                                reverse -> requestSort(false, screen.getClass().getName(), SortMode.DEFAULT, reverse)));
                    }
                    screensWithExtSort.add(screen);
                } catch (Exception e) {
                    Throwables.throwIfUnchecked(e);
                    throw new RuntimeException(e);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        if (screen.getFocused() instanceof EditBox) return;

        InputConstants.Key pressed = InputConstants.getKey(event.getKeyEvent());
        boolean playerMatch = KeyMappings.SORT_PLAYER.isActiveAndMatches(pressed);
        boolean externalMatch = KeyMappings.SORT_INVENTORY.isActiveAndMatches(pressed);
        boolean eitherMatch = KeyMappings.SORT_EITHER.isActiveAndMatches(pressed);

        if (playerMatch || externalMatch || eitherMatch) {
            InvTweaksMod.LOGGER.info("[InvTweaks] key {} matched in screen {} (player={} external={} either={})",
                    pressed.getName(), screen.getClass().getName(), playerMatch, externalMatch, eitherMatch);
        }

        if (InvTweaksConfig.isSortEnabled(true) && playerMatch) {
            requestSort(true, screen.getClass().getName());
        }
        if (InvTweaksConfig.isSortEnabled(false)
                && screensWithExtSort.contains(event.getScreen())
                && externalMatch) {
            requestSort(false, screen.getClass().getName());
        }

        if (eitherMatch) {
            Slot slot = screen.getHoveredSlot();
            if (slot != null) {
                boolean isPlayerSort = Utils.isPlayerContainer(slot.container, screen, Minecraft.getInstance().player);
                if (InvTweaksConfig.isSortEnabled(isPlayerSort)
                        && (isPlayerSort || screensWithExtSort.contains(event.getScreen()))) {
                    requestSort(isPlayerSort, screen.getClass().getName());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onMouseButtonPressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        if (screen instanceof CreativeModeInventoryScreen) return;

        boolean isMouseActive = KeyMappings.SORT_EITHER.getKeyConflictContext().isActive()
                && KeyMappings.SORT_EITHER.matchesMouse(event.getMouseButtonEvent());
        if (!isMouseActive) return;

        Slot slot = screen.getHoveredSlot();
        if (slot == null) return;

        Player player = Minecraft.getInstance().player;
        if (player != null && player.hasInfiniteMaterials() && !slot.getItem().isEmpty()) {
            return; // creative pick-block takes priority on non-empty slots
        }
        boolean isPlayerSort = Utils.isPlayerContainer(slot.container, screen, player);
        if (InvTweaksConfig.isSortEnabled(isPlayerSort)
                && (isPlayerSort || screensWithExtSort.contains(event.getScreen()))) {
            requestSort(isPlayerSort, screen.getClass().getName());
            event.setCanceled(true);
        }
    }

    /**
     * Quick-view: total count of held item shown next to the hotbar.
     * 26.1 removed {@code Gui#renderSlot}, so we draw a text count rather than a phantom slot.
     */
    @SubscribeEvent
    public static void renderOverlay(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.HOTBAR)) return;
        if (!InvTweaksConfig.isQuickViewEnabled()) return;

        Player ent = Minecraft.getInstance().player;
        if (ent == null) return;

        Ruleset rules = InvTweaksConfig.getSelfCompiledRules();
        IntList frozen = Optional.ofNullable(rules.catToInventorySlots("/FROZEN"))
                .map(IntArrayList::new)
                .orElseGet(IntArrayList::new);
        frozen.sort(null);

        Inventory inv = ent.getInventory();
        if (Collections.binarySearch(frozen, inv.getSelectedSlot()) >= 0) return;

        ItemStack mainHand = ent.getMainHandItem();
        if (mainHand.isEmpty()) return;

        int itemCount = IntStream.range(0, 36)
                .filter(idx -> Collections.binarySearch(frozen, idx) < 0)
                .mapToObj(inv::getItem)
                .filter(st -> ItemStack.isSameItemSameComponents(st, mainHand))
                .mapToInt(ItemStack::getCount)
                .sum();

        if (itemCount <= mainHand.getCount()) return; // nothing extra to show

        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        int centerX = graphics.guiWidth() / 2;
        int hotbarY = graphics.guiHeight() - 16 - 3;
        HumanoidArm dominant = ent.getMainArm();
        // Right of right-side hotbar; left of left-side hotbar (mirrors the original).
        int x = dominant == HumanoidArm.RIGHT ? centerX + 91 + 10 : centerX - 91 - 26;

        Component text = Component.literal("x" + itemCount);
        graphics.text(font, text, x, hotbarY + 4, 0xFFFFFFFF);
    }
}
