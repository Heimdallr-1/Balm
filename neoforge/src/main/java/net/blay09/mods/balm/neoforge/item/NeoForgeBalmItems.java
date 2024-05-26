package net.blay09.mods.balm.neoforge.item;

import com.google.common.collect.*;
import net.blay09.mods.balm.api.DeferredObject;
import net.blay09.mods.balm.api.item.BalmItems;
import net.blay09.mods.balm.neoforge.DeferredRegisters;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.fml.ModLoadingContext;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class NeoForgeBalmItems implements BalmItems {

    private static class Registrations {
        public final Multimap<ResourceLocation, Supplier<ItemLike[]>> creativeTabContents = ArrayListMultimap.create();
        private final Map<ResourceLocation, Comparator<ItemLike>> creativeTabSorting = new HashMap<>();

        public void buildCreativeTabContents(ResourceLocation tabIdentifier, CreativeModeTab.Output entries) {
            Collection<Supplier<ItemLike[]>> itemStackArraySuppliers = creativeTabContents.get(tabIdentifier);
            final var comparator = creativeTabSorting.get(tabIdentifier);
            if (!itemStackArraySuppliers.isEmpty()) {
                itemStackArraySuppliers.forEach(it -> {
                    final var itemStacks = Arrays.asList(it.get());
                    final var sortedItemStacks = comparator != null ? itemStacks.stream().sorted(comparator).toList() : itemStacks;
                    for (final var itemStack : sortedItemStacks) {
                        entries.accept(itemStack);
                    }
                });
            }
        }
    }

    private final Map<String, Registrations> registrations = new ConcurrentHashMap<>();

    @Override
    public Item.Properties itemProperties() {
        return new Item.Properties();
    }

    @Override
    public DeferredObject<Item> registerItem(Supplier<Item> supplier, ResourceLocation identifier, @Nullable ResourceLocation creativeTab) {
        final var register = DeferredRegisters.get(Registries.ITEM, identifier.getNamespace());
        final var registryObject = register.register(identifier.getPath(), supplier);
        if (creativeTab != null) {
            getActiveRegistrations().creativeTabContents.put(creativeTab, () -> new ItemLike[]{registryObject.get()});
        }
        return new DeferredObject<>(identifier, registryObject, registryObject::isBound);
    }

    @Override
    public DeferredObject<CreativeModeTab> registerCreativeModeTab(Supplier<ItemStack> iconSupplier, ResourceLocation identifier) {
        final var register = DeferredRegisters.get(Registries.CREATIVE_MODE_TAB, identifier.getNamespace());
        final var registryObject = register.register(identifier.getPath(), () -> {
            Component displayName = Component.translatable("itemGroup." + identifier.toString().replace(':', '.'));
            final var registrations = getActiveRegistrations();
            CreativeModeTab creativeModeTab = CreativeModeTab.builder()
                    .title(displayName)
                    .icon(iconSupplier)
                    .displayItems((enabledFeatures, entries) -> registrations.buildCreativeTabContents(identifier, entries))
                    .build();
            creativeModeTab = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, identifier, creativeModeTab);
            return creativeModeTab;
        });
        return new DeferredObject<>(identifier, registryObject, registryObject::isBound);
    }

    @Override
    public void addToCreativeModeTab(ResourceLocation tabIdentifier, Supplier<ItemLike[]> itemsSupplier) {
        getActiveRegistrations().creativeTabContents.put(tabIdentifier, itemsSupplier);
    }

    @Override
    public void setCreativeModeTabSorting(ResourceLocation tabIdentifier, Comparator<ItemLike> comparator) {
        getActiveRegistrations().creativeTabSorting.put(tabIdentifier, comparator);
    }

    private Registrations getActiveRegistrations() {
        return registrations.computeIfAbsent(ModLoadingContext.get().getActiveNamespace(), it -> new Registrations());
    }
}
