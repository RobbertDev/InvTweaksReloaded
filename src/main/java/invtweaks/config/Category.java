package invtweaks.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import invtweaks.InvTweaksMod;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.IntStream;

public class Category {
    private final List<String> spec;
    private final List<List<Predicate<ItemStack>>> compiledSpec = new ArrayList<>();

    public Category(List<String> spec) {
        this.spec = spec;
        for (String subspec : spec) {
            List<Predicate<ItemStack>> compiledSubspec = new ArrayList<>();
            for (String clause : subspec.split("\\s*;\\s*")) {
                compileClause(clause).ifPresent(compiledSubspec::add);
            }
            compiledSpec.add(compiledSubspec);
        }
    }

    public Category(String... spec) {
        this(Arrays.asList(spec));
    }

    private static Optional<Predicate<ItemStack>> compileClause(String clause) {
        if (clause.startsWith("!")) {
            return compileClause(clause.substring(1)).map(Predicate::negate);
        }

        String[] parts = clause.split(":", 2);
        if (parts[0].equals("/tag")) {
            TagKey<Item> itemKey = TagKey.create(BuiltInRegistries.ITEM.key(), Identifier.parse(parts[1]));
            TagKey<Block> blockKey = TagKey.create(BuiltInRegistries.BLOCK.key(), Identifier.parse(parts[1]));

            return Optional.of(stack -> stack.is(itemKey) || (
                    stack.getItem() instanceof BlockItem blockItem
                            && blockItem.getBlock().defaultBlockState().is(blockKey))
            );
        } else if (parts[0].equals("/instanceof") || parts[0].equals("/class")) {
            try {
                Class<?> clazz = Class.forName(parts[1]);
                if (parts[0].equals("/instanceof")) {
                    return Optional.of(st -> clazz.isInstance(st.getItem()));
                } else {
                    return Optional.of(st -> st.getItem().getClass().equals(clazz));
                }
            } catch (ClassNotFoundException e) {
                InvTweaksMod.LOGGER.warn("Class not found for clause '{}', ignoring", clause);
                return Optional.empty();
            }
        } else if (parts[0].equals("/isFood")) {
            return Optional.of(stack -> stack.get(DataComponents.FOOD) != null);
        } else {
            Identifier id = Identifier.tryParse(clause);
            if (id == null) {
                InvTweaksMod.LOGGER.warn("Invalid item resource location '{}', ignoring", clause);
                return Optional.empty();
            }
            return Optional.of(st -> Objects.equals(BuiltInRegistries.ITEM.getKey(st.getItem()), id));
        }
    }

    public int checkStack(ItemStack stack) {
        return IntStream.range(0, compiledSpec.size())
                .filter(idx -> compiledSpec.get(idx).stream().allMatch(pr -> pr.test(stack)))
                .findFirst()
                .orElse(-1);
    }

    public CommentedConfig toConfig(String catName) {
        CommentedConfig result = CommentedConfig.inMemory();
        result.set("name", catName);
        result.set("spec", spec);
        return result;
    }
}
