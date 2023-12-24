package dev.seeruk.monsooncraft.block;

import dev.seeruk.monsooncraft.MonsooncraftMod;
import dev.seeruk.monsooncraft.inventory.SimpleRecipeInputInventory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeType;
import net.minecraft.util.Pair;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class CompressorBlockEntity extends BlockEntity implements SidedInventory {
    private static final int INPUT_SLOT = 0;

    private static final int OUTPUT_SLOT = 1;

    protected DefaultedList<ItemStack> inventory;

    protected long compressCooldown;

    public CompressorBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(Blocks.COMPRESSOR_BLOCK_ENTITY, blockPos, blockState);
        this.inventory = DefaultedList.ofSize(2, ItemStack.EMPTY);
    }

    @Override
    public int size() {
        return this.inventory.size();
    }

    @Override
    public int[] getAvailableSlots(Direction side) {
        if (side == Direction.DOWN) {
            return new int[]{OUTPUT_SLOT};
        } else {
            return new int[]{INPUT_SLOT};
        }
    }

    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
        return dir != Direction.DOWN;
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        return dir == Direction.DOWN;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : this.inventory) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getStack(int slot) {
        return this.inventory.get(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        return Inventories.splitStack(this.inventory, slot, amount);
    }

    @Override
    public ItemStack removeStack(int slot) {
        return Inventories.removeStack(this.inventory, slot);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        this.inventory.set(slot, stack);
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return false;
    }

    @Override
    public void clear() {
        this.inventory.clear();
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        this.inventory = DefaultedList.ofSize(this.size(), ItemStack.EMPTY);
        Inventories.readNbt(nbt, this.inventory);
        this.compressCooldown = nbt.getLong("CompressCooldown");
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        Inventories.writeNbt(nbt, this.inventory);
        nbt.putLong("CompressCooldown", this.compressCooldown);
    }

    public static void tick(World world, BlockPos pos, BlockState state, CompressorBlockEntity blockEntity) {
        --blockEntity.compressCooldown;
        if (!blockEntity.needsCooldown()) {
            blockEntity.setCompressCooldown(0); // Ensure it's not below 0 at this point.
            tryCraft(blockEntity);
        }
    }

    private static void tryCraft(CompressorBlockEntity blockEntity) {
        var inputStack = blockEntity.inventory.get(INPUT_SLOT);
        if (inputStack == null) {
            MonsooncraftMod.LOGGER.info("can't craft because input is empty, or not the right size");
            return; // Silently fail...
        }

        var maybeRecipe = tryGetRecipe(blockEntity);
        if (maybeRecipe.isPresent()) {
            var recipe = maybeRecipe.get().getLeft();
            var inventory = maybeRecipe.get().getRight();

            var result = recipe.craft(inventory, blockEntity.world.getRegistryManager());

            if (result.isEmpty()) {
                MonsooncraftMod.LOGGER.info("result of craft was empty");
                return; // Nothing to do...
            }

            var outputStack = blockEntity.inventory.get(OUTPUT_SLOT);

            // TODO: Refactor the below...

            if (outputStack.isEmpty()) {
                blockEntity.inventory.set(OUTPUT_SLOT, result);
                blockEntity.inventory.get(INPUT_SLOT).decrement(inventory.stacks.size());
            }

            if (ItemStack.canCombine(outputStack, result) && outputStack.getMaxCount() >= outputStack.getCount() + result.getCount()) {
                blockEntity.inventory.set(OUTPUT_SLOT, outputStack.copyWithCount(outputStack.getCount() + result.getCount()));
                blockEntity.inventory.get(INPUT_SLOT).decrement(inventory.stacks.size());
            }
        } else {
            MonsooncraftMod.LOGGER.info("no recipe found");
        }
    }

    private static Optional<Pair<CraftingRecipe, SimpleRecipeInputInventory>> tryGetRecipe(CompressorBlockEntity blockEntity) {
        // TODO: We can refactor this later...
        if (blockEntity.inventory.get(INPUT_SLOT).getCount() >= 4) {
            var inventory = blockEntity.createFilledInventoryOf(blockEntity.inventory.get(INPUT_SLOT), 4);
            var recipe = blockEntity.world.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, inventory, blockEntity.world);
            if (!recipe.isEmpty()) {
                MonsooncraftMod.LOGGER.info("found recipe with 4 slots");
                return Optional.of(new Pair(recipe.get(), inventory));
            }
        }

        if (blockEntity.inventory.get(INPUT_SLOT).getCount() >= 9) {
            var inventory = blockEntity.createFilledInventoryOf(blockEntity.inventory.get(INPUT_SLOT), 9);
            var recipe = blockEntity.world.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, inventory, blockEntity.world);
            if (!recipe.isEmpty()) {
                MonsooncraftMod.LOGGER.info("found recipe with 9 slots");
                return Optional.of(new Pair(recipe.get(), inventory));
            }
        }

        MonsooncraftMod.LOGGER.info("no recipe found at all");
        return Optional.empty();
    }

    private SimpleRecipeInputInventory createFilledInventoryOf(ItemStack itemStack, int size) {
        var inventory = new SimpleRecipeInputInventory(size);
        for (int i = 0; i < size; i++) {
            inventory.setStack(i, itemStack.copyWithCount(1));
        }
        return inventory;
    }

    private void setCompressCooldown(int compressCooldown) {
        this.compressCooldown = compressCooldown;
    }

    private boolean needsCooldown() {
        return this.compressCooldown > 0;
    }
}

