package com.raccseal.immersivegardening.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.raccseal.immersivegardening.ImmersiveGardeningPlugin;
import com.raccseal.immersivegardening.component.BoundPlantEntityComponent;
import com.raccseal.immersivegardening.component.PlantDisplayComponent;
import com.raccseal.immersivegardening.util.PlantDisplayUtil;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;


// Suppress deprecated warning because getBlockRotation is deprecated and has no replacement afaik
@SuppressWarnings({"deprecation", "removal"})
public class PlanterInteraction extends SimpleBlockInteraction {
    public static final BuilderCodec<PlanterInteraction> CODEC = BuilderCodec.builder(PlanterInteraction.class, PlanterInteraction::new, SimpleBlockInteraction.CODEC).documentation("Places/Removes plant from planter block").build();

    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Override
    protected void interactWithBlock(@Nonnull World world, @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull InteractionType type, @Nonnull InteractionContext context, @Nullable ItemStack itemInHand, @Nonnull Vector3i targetBlock, @Nonnull CooldownHandler cooldownHandler) {
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
        PlantDisplayComponent displayComp = null;

        // Check if there's an existing plant display entity attached to this planter and get its display component if it exists
        if (boundEntityComp != null && boundEntityComp.hasAttachedEntities()) {
            // Get the first entity's display component to check if there's a plant
            UUID firstEntityUUID = boundEntityComp.getAttachedEntities().getFirst();
            Ref<EntityStore> firstDisplayRef = world.getEntityRef(firstEntityUUID);
            if (firstDisplayRef != null) {
                displayComp = commandBuffer.getComponent(firstDisplayRef, ImmersiveGardeningPlugin.get().getPlantDisplayComponent());
            }
        }

        ItemStack heldItem = context.getHeldItem();

        boolean isHoldingPlant = false;
        if (heldItem != null) {
            isHoldingPlant = Arrays.asList(heldItem.getItem().getCategories()).contains("Blocks.Plants");
        }
        boolean hasPlant = displayComp != null && displayComp.getHeldStack() != null && !displayComp.getHeldStack().isEmpty() ;

        if (hasPlant) {
            ItemStack storedPlant = displayComp.getHeldStack();
            if (storedPlant != null && !storedPlant.isEmpty()) {
                this.spawnItemDrop(commandBuffer, storedPlant, targetBlock);
            }

            // Remove ALL attached entities
            if (boundEntityComp.hasAttachedEntities()) {
                for (UUID entityUUID : boundEntityComp.getAttachedEntities()) {
                    Ref<EntityStore> entityRef = world.getEntityRef(entityUUID);
                    commandBuffer.run((store) -> PlantDisplayUtil.removePlantEntity(store, entityRef));
                }
            }

            boundEntityComp.clearAttachedEntities();
            world.performBlockUpdate(x, y, z);
        } else if (isHoldingPlant) {
            ItemContainer heldItemContainer = context.getHeldItemContainer();
            if (heldItemContainer == null) {
                context.getState().state = InteractionState.Failed;
                ((HytaleLogger.Api) LOGGER.atWarning()).log("Could not get held item container");
                return;
            }

            short heldItemSlot = (short) context.getHeldItemSlot();
            ItemStack plantToStore = heldItem.withQuantity(1);
            Vector3d[] entityOffsets = plantPositions(blockType.getId());
            int rotation = worldchunk.getRotationIndex(targetBlock.x, targetBlock.y, targetBlock.z);

            //Rotate entities to align with block rotation
            int yawDegrees = -rotation * 90;
            for (int i = 0; i < entityOffsets.length; i++) {
                entityOffsets[i] = this.rotateOffsetByYaw(entityOffsets[i], yawDegrees).add(0.5, 0.0, 0.5);
            }

            Ref<ChunkStore> finalChunkRef = chunkRef;
            commandBuffer.run((store) -> {
                // Remove all existing entities first
                Store<ChunkStore> chunkstore = world.getChunkStore().getStore();
                BoundPlantEntityComponent existingBoundComp = chunkstore.getComponent(finalChunkRef, ImmersiveGardeningPlugin.get().getBoundPlantEntityComponent());

                if (existingBoundComp != null && existingBoundComp.hasAttachedEntities()) {
                    for (UUID oldEntityUUID : new java.util.ArrayList<>(existingBoundComp.getAttachedEntities())) {
                        Ref<EntityStore> oldEntityRef = world.getEntityRef(oldEntityUUID);
                        if (oldEntityRef != null) {
                            store.removeEntity(oldEntityRef, RemoveReason.REMOVE);
                        }
                    }
                    existingBoundComp.clearAttachedEntities();
                }

                UUID[] newEntityUUIDs = PlantDisplayUtil.remakePlantEntities(store, null, plantToStore, targetBlock, entityOffsets);

                if (newEntityUUIDs == null || newEntityUUIDs.length == 0) {
                    context.getState().state = InteractionState.Failed;
                    LOGGER.atWarning().log("Failed to create plant display entity");
                    return;
                }

                BoundPlantEntityComponent newBoundComp = chunkstore.getComponent(finalChunkRef, ImmersiveGardeningPlugin.get().getBoundPlantEntityComponent());

                if (newBoundComp == null) {
                    List<UUID> uuidList = new java.util.ArrayList<>();
                    Collections.addAll(uuidList, newEntityUUIDs);
                    chunkstore.putComponent(finalChunkRef, ImmersiveGardeningPlugin.get().getBoundPlantEntityComponent(), new BoundPlantEntityComponent(uuidList));
                } else {
                    for (UUID newEntityUUID : newEntityUUIDs) {
                        newBoundComp.addAttachedEntity(newEntityUUID);
                    }
                }

                ItemStackSlotTransaction transaction = heldItemContainer.removeItemStackFromSlot(heldItemSlot, heldItem, 1);
                if (!transaction.succeeded()) {
                    context.getState().state = InteractionState.Failed;
                    LOGGER.atWarning().log("Failed to remove item from player inventory");
                }

            });
            world.performBlockUpdate(x, y, z);
        } else {
            context.getState().state = InteractionState.Failed;
        }
    }

    private void spawnItemDrop(CommandBuffer<EntityStore> commandBuffer, ItemStack itemStack, Vector3i blockPos) {
        Vector3d dropPosition = blockPos.toVector3d().add(0.5, 0.5, 0.5);
        Holder<EntityStore> itemHolder = ItemComponent.generateItemDrop(commandBuffer, itemStack, dropPosition, Vector3f.ZERO, 0.0F, 0.0F, 0.0F);
        if (itemHolder != null) {
            commandBuffer.addEntity(itemHolder, AddReason.SPAWN);
        }

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
        LOGGER.atInfo().log("BLOCK ID: " + blockId);
        switch (blockId) {
            case "Furniture_Hardwood_Planter_Small" -> {
                return new Vector3d[]{new Vector3d(0.3, 0.9, -0.35), new Vector3d(0.0, 0.9, -0.35), new Vector3d(-0.3, 0.9, -0.35)};
            }
            case "Furniture_Hardwood_Planter_Pot" -> {
                return new Vector3d[]{new Vector3d(0.0, 0.6, 0.0)};
            }
            default -> {
                return new Vector3d[]{new Vector3d((double) 0.5F, (double) 0.5F, (double) 0.5F)};
            }
        }
    }
}
