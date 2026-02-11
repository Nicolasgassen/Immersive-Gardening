package com.raccseal.immersivegardening.interaction;
import com.raccseal.immersivegardening.ImmersiveGardeningPlugin;
import com.raccseal.immersivegardening.component.BoundPlantEntityComponent;
import com.raccseal.immersivegardening.component.PlantDisplayComponent;
import com.raccseal.immersivegardening.util.PlantDisplayUtil;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.UUID;


// Suppress deprecated warning because getBlockRotation is deprecated and has no replacement afaik
@SuppressWarnings({"deprecation"})
public class PlanterRemovePlantInteraction extends SimpleBlockInteraction {
    public static final BuilderCodec<PlanterRemovePlantInteraction> CODEC = BuilderCodec.builder(PlanterRemovePlantInteraction.class, PlanterRemovePlantInteraction::new, SimpleBlockInteraction.CODEC).documentation("Places/Removes plant from planter block").build();
    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Override
    protected void interactWithBlock(@NonNull World world, @NonNull CommandBuffer<EntityStore> commandBuffer, @NonNull InteractionType type, @NonNull InteractionContext context, @Nullable ItemStack itemInHand, @NonNull Vector3i targetBlock, @NonNull CooldownHandler cooldownHandler) {
        int x = targetBlock.getX();
        int y = targetBlock.getY();
        int z = targetBlock.getZ();
        long indexChunk = ChunkUtil.indexChunkFromBlock(x, z);

        WorldChunk worldChunk = world.getChunk(indexChunk);
        if (worldChunk == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        BlockType blockType = worldChunk.getBlockType(targetBlock);
        if (blockType == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        Ref<ChunkStore> chunkRef = worldChunk.getBlockComponentEntity(x, y, z);
        if (chunkRef == null) {
            chunkRef = BlockModule.ensureBlockEntity(worldChunk, x, y, z);
        }

        if (chunkRef == null) {
            context.getState().state = InteractionState.Failed;
            LOGGER.atWarning().log("Failed to create block entity for planter at " + targetBlock);
            return;
        }

        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        BoundPlantEntityComponent boundEntityComp = chunkStore.getComponent(chunkRef, ImmersiveGardeningPlugin.get().getBoundPlantEntityComponent());

        if (boundEntityComp != null && boundEntityComp.hasAttachedEntities()) {

            // Remove all attached entities and drop their plants
            for (UUID entityUUID : new java.util.ArrayList<>(boundEntityComp.getAttachedEntities())) {
                Ref<EntityStore> entityRef = world.getEntityRef(entityUUID);
                if (entityRef != null) {
                    // Get the display component for THIS specific entity to get its plant
                    PlantDisplayComponent displayComp = commandBuffer.getComponent(entityRef, ImmersiveGardeningPlugin.get().getPlantDisplayComponent());
                    if (displayComp != null && displayComp.getHeldStack() != null) {
                        ItemStack storedPlant = displayComp.getHeldStack();
                        this.spawnItemDrop(commandBuffer, storedPlant, targetBlock);
                    }
                    commandBuffer.run((store) -> PlantDisplayUtil.removePlantEntity(store, entityRef));
                }
            }

            boundEntityComp.clearAttachedEntities();
            world.performBlockUpdate(x, y, z);
        }
        else {
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
}
