package invtweaks.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;

public enum SortMode {
    DEFAULT,
    NAME,
    COUNT,
    MOD;

    public static SortMode fromOrdinal(int ord) {
        SortMode[] vs = values();
        if (ord < 0 || ord >= vs.length) return DEFAULT;
        return vs[ord];
    }

    public Comparator<ItemStack> comparator() {
        return comparator(false);
    }

    public Comparator<ItemStack> comparator(boolean reverse) {
        Comparator<ItemStack> base = switch (this) {
            case NAME -> Comparator
                    .<ItemStack, String>comparing(s -> s.getHoverName().getString())
                    .thenComparing(Utils.FALLBACK_COMPARATOR);
            case COUNT -> Comparator
                    .comparingInt(ItemStack::getCount).reversed()
                    .thenComparing(Utils.FALLBACK_COMPARATOR);
            case MOD -> Comparator
                    .<ItemStack, String>comparing(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace())
                    .thenComparing(Utils.FALLBACK_COMPARATOR);
            case DEFAULT -> Utils.FALLBACK_COMPARATOR;
        };
        return reverse ? base.reversed() : base;
    }
}
