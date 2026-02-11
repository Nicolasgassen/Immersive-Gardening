package com.raccseal.immersivegardening.system;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.raccseal.immersivegardening.ImmersiveGardeningPlugin;
import com.raccseal.immersivegardening.component.BoundPlantEntityComponent;
import com.raccseal.immersivegardening.component.PlantDisplayComponent;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import org.jspecify.annotations.NonNull;

/**
 * Systems for handling planter block events and plant display entity lifecycle.
 */
public class PlanterSystems {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Check if the given block type corresponds to a planter block.
     * Checks if the block ID contains "Planter" and Immersive_Gardening
     */
    public static boolean isPlanterBlock(BlockType blockType) {
        if (blockType == null) {
            return false;
        }

        String blockId = blockType.getId();
        if (blockId == null) {
            return false;
        }

        // Check if the block ID contains "Planter"
        return blockId.contains("Planter") && blockId.contains("Immersive_Gardening");
    }

    /**
     * System that handles breaking planter blocks.
     * Removes the associated plant display entity and drops the plant item.
     */
    public static class BreakPlanterSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

        public BreakPlanterSystem() {
            super(BreakBlockEvent.class);
        }

        @Override
        public void handle(int index,
                           @NonNull ArchetypeChunk<EntityStore> archetypeChunk,
                           @NonNull Store<EntityStore> store,
                           @NonNull CommandBuffer<EntityStore> commandBuffer,
                           @NonNull BreakBlockEvent event) {
            World world = commandBuffer.getExternalData().getWorld();
            Vector3i targetBlock = event.getTargetBlock();


            int x = targetBlock.getX();
            int y = targetBlock.getY();
            int z = targetBlock.getZ();
            long indexChunk = ChunkUtil.indexChunkFromBlock(x, z);
            WorldChunk worldchunk = world.getChunk(indexChunk);

            if (worldchunk == null) return;

            BlockType blockType = worldchunk.getBlockType(targetBlock);
            if (!isPlanterBlock(blockType)) {
                return;
            }

            Ref<ChunkStore> chunkRef = worldchunk.getBlockComponentEntity(x, y, z);
            if (chunkRef == null) {
                return;
            }

            Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
            BoundPlantEntityComponent boundEntityComp = chunkStore.getComponent(
                    chunkRef,
                    ImmersiveGardeningPlugin.get().getBoundPlantEntityComponent()
            );

            if (boundEntityComp == null || !boundEntityComp.hasAttachedEntities()) {
                return;
            }

            for (java.util.UUID entityUUID : new java.util.ArrayList<>(boundEntityComp.getAttachedEntities())) {
                Ref<EntityStore> displayRef = world.getEntityRef(entityUUID);
                if (displayRef == null) {
                    continue;
                }

                PlantDisplayComponent displayComp = store.getComponent(
                        displayRef,
                        ImmersiveGardeningPlugin.get().getPlantDisplayComponent()
                );

                if (displayComp != null && displayComp.getHeldStack() != null && !displayComp.getHeldStack().isEmpty()) {
                    ItemStack plantItem = displayComp.getHeldStack();
                    spawnItemDrop(commandBuffer, plantItem, targetBlock);
                }
                commandBuffer.removeEntity(displayRef, RemoveReason.REMOVE);
            }

            boundEntityComp.clearAttachedEntities();
        }

        /**
         * Spawns an item drop at the specified block position.
         */
        private void spawnItemDrop(CommandBuffer<EntityStore> commandBuffer, ItemStack itemStack, Vector3i blockPos) {
            Vector3d randomOffset = new Vector3d(
                    (Math.random() - 0.2) * 0.2,
                    1.1,
                    (Math.random() - 0.2) * 0.2
            );
            Vector3d dropPosition = blockPos.toVector3d().add(randomOffset);

            Holder<EntityStore> itemHolder = ItemComponent.generateItemDrop(
                    commandBuffer,
                    itemStack,
                    dropPosition,
                    Vector3f.ZERO,
                    0f, 0f, 0f
            );

            if (itemHolder != null) {
                ItemComponent itemComponent = itemHolder.getComponent(ItemComponent.getComponentType());
                if (itemComponent != null) {
                    itemComponent.setPickupDelay(0.0f);
                }
                commandBuffer.addEntity(itemHolder, AddReason.SPAWN);
            }
        }

        @NullableDecl
        @Override
        public Query<EntityStore> getQuery() {
            return PlayerRef.getComponentType();
        }
    }

    /**
     * Tick system that periodically checks if the planter block still exists.
     * If the planter block is gone, it removes the plant display entity and drops the item.
     * This handles edge cases where the block is destroyed without triggering the break event.
     */
    public static class PlantDisplayTick extends EntityTickingSystem<EntityStore> {
        private int tickCounter = 0;
        private static final int TICKS_PER_CHECK = 10;

        @Override
        public void tick(float dt,
                         int index,
                         @NonNull ArchetypeChunk<EntityStore> archetypeChunk,
                         @NonNull Store<EntityStore> store,
                         @NonNull CommandBuffer<EntityStore> commandBuffer) {
            tickCounter++;
            if (tickCounter < TICKS_PER_CHECK) {
                return;
            }
            tickCounter = 0;

            Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
            PlantDisplayComponent component = store.getComponent(ref, ImmersiveGardeningPlugin.get().getPlantDisplayComponent());

            if (component == null) {
                return;
            }

            Vector3i planterPosition = component.getPlanterPosition();
            ItemStack heldStack = component.getHeldStack();

            // If no planter position is set, remove the entity
            if (planterPosition == null) {
                commandBuffer.run((entityStore) -> {
                    if (heldStack != null && !heldStack.isEmpty()) {
                        spawnItemDropFromStore(entityStore, heldStack, Vector3i.ZERO);
                    }
                    store.removeEntity(ref, RemoveReason.REMOVE);
                });
                return;
            }

            // Check if the planter block still exists
            World world = store.getExternalData().getWorld();
            BlockType blockType = world.getBlockType(planterPosition);

            if (!isPlanterBlock(blockType)) {
                LOGGER.atInfo().log("Planter block no longer exists at " + planterPosition + ", removing display entity and dropping plant: " + (heldStack != null ? heldStack.getItemId() : "null"));

                commandBuffer.run((entityStore) -> {
                    if (heldStack != null && !heldStack.isEmpty()) {
                        spawnItemDropFromStore(entityStore, heldStack, planterPosition);
                    }
                    store.removeEntity(ref, RemoveReason.REMOVE);
                });
            }
        }

        /**
         * Spawns an item drop from the entity store.
         */
        private void spawnItemDropFromStore(Store<EntityStore> store, ItemStack itemStack, Vector3i blockPos) {
            Vector3d dropPosition = blockPos.toVector3d().add(0.5, 1.0, 0.5);

            Holder<EntityStore> itemHolder = ItemComponent.generateItemDrop(
                    store,
                    itemStack,
                    dropPosition,
                    Vector3f.ZERO,
                    0f, 0f, 0f
            );

            if (itemHolder != null) {
                ItemComponent itemComponent = itemHolder.getComponent(ItemComponent.getComponentType());
                if (itemComponent != null) {
                    itemComponent.setPickupDelay(0.5f);
                }
                store.addEntity(itemHolder, AddReason.SPAWN);
            }
        }

        @NullableDecl
        @Override
        public Query<EntityStore> getQuery() {
            return ImmersiveGardeningPlugin.get().getPlantDisplayComponent();
        }
    }
}



