package com.raccseal.immersivegardening.interaction;

import com.raccseal.immersivegardening.ImmersiveGardeningPlugin;
import com.raccseal.immersivegardening.component.BoundPlantEntityComponent;
import com.raccseal.immersivegardening.component.PlantDisplayComponent;
import com.raccseal.immersivegardening.util.PlantDisplayUtil;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;


// Suppress deprecated warning because getBlockRotation is deprecated and has no replacement afaik
@SuppressWarnings({"deprecation", "removal"})
public class PlanterInsertPlantInteraction extends SimpleBlockInteraction {
    public static final BuilderCodec<PlanterInsertPlantInteraction> CODEC = BuilderCodec.builder(PlanterInsertPlantInteraction.class, PlanterInsertPlantInteraction::new, SimpleBlockInteraction.CODEC).documentation("Places/Removes plant from planter block").build();
    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Override
    protected void interactWithBlock(@NonNull World world, @NonNull CommandBuffer<EntityStore> commandBuffer, @NonNull InteractionType type, @NonNull InteractionContext context, @Nullable ItemStack itemInHand, @NonNull Vector3i targetBlock, @NonNull CooldownHandler cooldownHandler) {
        ItemStack heldItem = context.getHeldItem();

        // Accept items that ARE plants (must contain "Blocks.Plants" category)
        if (heldItem == null || !Arrays.asList(heldItem.getItem().getCategories()).contains("Blocks.Plants")) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        int x = targetBlock.getX();
        int y = targetBlock.getY();
        int z = targetBlock.getZ();
        long indexChunk = ChunkUtil.indexChunkFromBlock(x, z);

        WorldChunk worldchunk = world.getChunk(indexChunk);
        if (worldchunk == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        BlockType blockType = worldchunk.getBlockType(targetBlock);
        if (blockType == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        Ref<ChunkStore> chunkRef = worldchunk.getBlockComponentEntity(x, y, z);
        if (chunkRef == null) {
            chunkRef = BlockModule.ensureBlockEntity(worldchunk, x, y, z);
        }

        if (chunkRef == null) {
            context.getState().state = InteractionState.Failed;
            LOGGER.atWarning().log("Failed to create block entity for planter at " + targetBlock);
            return;
        }

        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        BoundPlantEntityComponent boundEntityComp = chunkStore.getComponent(chunkRef, ImmersiveGardeningPlugin.get().getBoundPlantEntityComponent());
        final int maxPlants = plantPositions(blockType.getId()).length;

        if (boundEntityComp != null && boundEntityComp.hasAttachedEntities()) {
            int currentPlantCount = boundEntityComp.getAttachedEntities().size();
            if (currentPlantCount >= maxPlants) {
                context.getState().state = InteractionState.Failed;
                return;
            }
        }

        ItemContainer heldItemContainer = context.getHeldItemContainer();
        if (heldItemContainer == null) {
            context.getState().state = InteractionState.Failed;
            LOGGER.atWarning().log("Could not get held item container");
            return;
        }

        short heldItemSlot = context.getHeldItemSlot();
        ItemStack plantToStore = heldItem.withQuantity(1);
        Vector3d[] allEntityOffsets = plantPositions(blockType.getId());

        int rotation = worldchunk.getRotationIndex(targetBlock.x, targetBlock.y, targetBlock.z);

        //Rotate entities to align with block rotation
        int yawDegrees = -rotation * 90;
        for (int i = 0; i < allEntityOffsets.length; i++) {
            allEntityOffsets[i] = this.rotateOffsetByYaw(allEntityOffsets[i], yawDegrees).add(0.5, 0.0, 0.5);
        }

        Ref<ChunkStore> finalChunkRef = chunkRef;
        commandBuffer.run((store) -> {
            // Collect all existing plant items before removing entities
            Store<ChunkStore> chunkstore = world.getChunkStore().getStore();
            BoundPlantEntityComponent existingBoundComp = chunkstore.getComponent(finalChunkRef, ImmersiveGardeningPlugin.get().getBoundPlantEntityComponent());
            
            List<ItemStack> existingPlantItems = new java.util.ArrayList<>();
            
            if (existingBoundComp != null && existingBoundComp.hasAttachedEntities()) {
                for (UUID oldEntityUUID : new java.util.ArrayList<>(existingBoundComp.getAttachedEntities())) {
                    Ref<EntityStore> oldEntityRef = world.getEntityRef(oldEntityUUID);
                    if (oldEntityRef != null) {
                        // Extract the plant item from the display component before removing the entity
                        PlantDisplayComponent plantDisplay = store.getComponent(oldEntityRef, ImmersiveGardeningPlugin.get().getPlantDisplayComponent());
                        if (plantDisplay != null && plantDisplay.getHeldStack() != null) {
                            existingPlantItems.add(plantDisplay.getHeldStack());
                        }
                        store.removeEntity(oldEntityRef, RemoveReason.REMOVE);
                    }
                }
                existingBoundComp.clearAttachedEntities();
            }
            
            // Combine existing plants with the new plant
            ItemStack[] allPlants = new ItemStack[existingPlantItems.size() + 1];
            for (int i = 0; i < existingPlantItems.size(); i++) {
                allPlants[i] = existingPlantItems.get(i);
            }
            allPlants[existingPlantItems.size()] = plantToStore;

            // Only use as many offsets as we have plants, but never more than available slots
            int entitiesToCreate = Math.min(allPlants.length, allEntityOffsets.length);
            Vector3d[] limitedOffsets = new Vector3d[entitiesToCreate];
            System.arraycopy(allEntityOffsets, 0, limitedOffsets, 0, entitiesToCreate);
            ItemStack[] limitedPlants = new ItemStack[entitiesToCreate];
            System.arraycopy(allPlants, 0, limitedPlants, 0, entitiesToCreate);


            UUID[] newEntityUUIDs = PlantDisplayUtil.remakePlantEntities(store, null, limitedPlants, targetBlock, limitedOffsets);

            if (newEntityUUIDs == null || newEntityUUIDs.length == 0) {
                context.getState().state = InteractionState.Failed;
                LOGGER.atWarning().log("Failed to create plant display entity");
                return;
            }

            BoundPlantEntityComponent newBoundComp = chunkstore.getComponent(finalChunkRef, ImmersiveGardeningPlugin.get().getBoundPlantEntityComponent());

            if (newBoundComp == null) {
                List<UUID> uuidList = new java.util.ArrayList<>();
                for (UUID uuid : newEntityUUIDs) {
                    if (uuid != null) {
                        uuidList.add(uuid);
                    }
                }
                chunkstore.putComponent(finalChunkRef, ImmersiveGardeningPlugin.get().getBoundPlantEntityComponent(), new BoundPlantEntityComponent(uuidList));
            } else {
                int addedCount = 0;
                for (UUID newEntityUUID : newEntityUUIDs) {
                    if (newEntityUUID != null) {
                        newBoundComp.addAttachedEntity(newEntityUUID);
                        addedCount++;
                    }
                }
            }

            ItemStackSlotTransaction transaction = heldItemContainer.removeItemStackFromSlot(heldItemSlot, heldItem, 1);
            if (!transaction.succeeded()) {
                context.getState().state = InteractionState.Failed;
                LOGGER.atWarning().log("Failed to remove item from player inventory");
            }

        });
        world.performBlockUpdate(x, y, z);
    }

    protected void simulateInteractWithBlock(@NonNull InteractionType type, @NonNull InteractionContext context, @Nullable ItemStack itemInHand, @NonNull World world, @NonNull Vector3i targetBlock) {
        //No-op since client side prediction isn't necessary for this interaction
    }

    private Vector3d rotateOffsetByYaw(Vector3d offset, int yawDegrees) {
        double radians = Math.toRadians(yawDegrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double rotatedX = offset.getX() * cos - offset.getZ() * sin;
        double rotatedZ = offset.getX() * sin + offset.getZ() * cos;
        return new Vector3d(rotatedX, offset.getY(), rotatedZ);
    }


    private Vector3d[] plantPositions(String blockId) {
        if(blockId.contains("Tall")) {
            return new Vector3d[]{new Vector3d(0.3, 0.9, -0.35), new Vector3d(0.0, 0.9, -0.35), new Vector3d(-0.3, 0.9, -0.35)};
        }
        else if(blockId.contains("Pot")) {
            return new Vector3d[]{new Vector3d(0.0, 0.55, 0.0)};
        }
        return new Vector3d[]{new Vector3d(0.0, 0.5, 0.0)};
    }
}
