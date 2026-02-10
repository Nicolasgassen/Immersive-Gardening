package com.raccseal.immersivegardening.util;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.Axis;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.item.config.AssetIconProperties;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.PropComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.item.PreventItemMerging;
import com.hypixel.hytale.server.core.modules.entity.item.PreventPickup;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.prefab.PrefabCopyableComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.raccseal.immersivegardening.ImmersiveGardeningPlugin;
import com.raccseal.immersivegardening.component.PlantDisplayComponent;
import org.jspecify.annotations.Nullable;
import java.util.UUID;

public class PlantDisplayUtil {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Creates or updates the display entities for plants in a planter.
     *
     * @param store       The entity store
     * @param existingRef The existing display entity ref (can be null)
     * @param plantItem   The item to display
     * @param planterPos  The position of the planter block
     * @param yawDegrees  The rotations of the plant entities in degrees
     * @return The UUIDs of the new display entity, or null if creation failed
     */
    @Nullable
    public static UUID[] remakePlantEntities(Store<EntityStore> store, @Nullable Ref<EntityStore> existingRef, @Nullable ItemStack plantItem, Vector3i planterPos, Vector3d[] entityOffsets) {
        if (existingRef != null) {
            store.removeEntity(existingRef, RemoveReason.REMOVE);
        }
        if (plantItem == null || plantItem.isEmpty()) {
            return null;
        }

        final int entityCount = entityOffsets.length;

        float scale = 0.5f;
        Item item = plantItem.getItem();
        AssetIconProperties iconProps = item.getIconProperties();
        if (iconProps != null) {
            scale = iconProps.getScale();
        }

        // Initialize all holders
        UUID[] uuids = new UUID[entityCount];
        for (int i = 0; i < entityCount; i++) {

            Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
            Vector3d displayPosition = planterPos.toVector3d().add(entityOffsets[i]);
            Vector3f rotation = new Vector3f();
            UUID entityUUID = UUID.randomUUID();

            rotation.addRotationOnAxis(Axis.Y, (int) (Math.random() * 360));
            holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(displayPosition, rotation));
            holder.addComponent(HeadRotation.getComponentType(), new HeadRotation(rotation));
            holder.addComponent(NetworkId.getComponentType(), new NetworkId(store.getExternalData().takeNextNetworkId()));
            holder.putComponent(UUIDComponent.getComponentType(), new UUIDComponent(entityUUID));

            // Add item display component with max pickup delay to prevent pickup
            ItemStack displayStack = new ItemStack(plantItem.getItemId(), 1);
            displayStack.setOverrideDroppedItemAnimation(true);
            ItemComponent itemComponent = new ItemComponent(displayStack);
            itemComponent.setPickupDelay(Float.MAX_VALUE);

            holder.addComponent(ItemComponent.getComponentType(), itemComponent);
            holder.addComponent(ImmersiveGardeningPlugin.get().getPlantDisplayComponent(), new PlantDisplayComponent(plantItem.withQuantity(1), planterPos));
            holder.addComponent(PreventPickup.getComponentType(), PreventPickup.INSTANCE);
            holder.addComponent(PreventItemMerging.getComponentType(), PreventItemMerging.INSTANCE);
            holder.addComponent(EntityScaleComponent.getComponentType(), new EntityScaleComponent(scale * 0.7f));
            holder.ensureComponent(PropComponent.getComponentType());
            holder.ensureComponent(PrefabCopyableComponent.getComponentType());
            if (item.hasBlockType()) {
                holder.addComponent(BlockEntity.getComponentType(), new BlockEntity(plantItem.getItemId()));
            }

            // Add the new entity to the world
            Ref<EntityStore> newRef = store.addEntity(holder, AddReason.SPAWN);
            if (newRef == null) {
                LOGGER.atWarning().log("Failed to create plant display entity at " + planterPos);
                continue;
            }
            uuids[i] = entityUUID;
        }
        return uuids;
    }

    public static UUID[] remakePlantEntities(
            Store<EntityStore> store,
            @Nullable Ref<EntityStore> existingRef,
            @Nullable ItemStack plantItem,
            Vector3i planterPos,
            Vector3d entityOffsets
    ) {
        return remakePlantEntities(
                store,
                existingRef,
                plantItem,
                planterPos,
                new Vector3d[] { entityOffsets }
        );
    }


    /**
     * Removes a plant display entity completely.
     */
    public static void removePlantEntity(Store<EntityStore> store, @Nullable Ref<EntityStore> entityRef) {
        if (entityRef != null) {
            store.removeEntity(entityRef, RemoveReason.REMOVE);
        }
    }
}
