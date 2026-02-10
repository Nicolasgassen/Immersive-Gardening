package com.raccseal.immersivegardening.component;

import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Component that stores the references to the display entities showing plants in the planter.
 * This is attached to the block entity (ChunkStore) and references entities in the EntityStore.
 */
public class BoundPlantEntityComponent implements Component<ChunkStore> {
    public static final BuilderCodec<BoundPlantEntityComponent> CODEC = BuilderCodec.builder(BoundPlantEntityComponent.class, BoundPlantEntityComponent::new)
            .append(new KeyedCodec<>("AttachedEntities", new ArrayCodec<>(Codec.UUID_BINARY, UUID[]::new)),
                    BoundPlantEntityComponent::setAttachedEntitiesArray,
                    BoundPlantEntityComponent::getAttachedEntitiesArray)
            .add()
            .build();

    private List<UUID> attachedEntities;

    public BoundPlantEntityComponent() {
        this.attachedEntities = new ArrayList<>();
    }

    public BoundPlantEntityComponent(List<UUID> attachedEntities) {
        this.attachedEntities = new ArrayList<>(attachedEntities);
    }

    public List<UUID> getAttachedEntities() {
        return attachedEntities;
    }

    public void setAttachedEntities(List<UUID> attachedEntities) {
        this.attachedEntities = new ArrayList<>(attachedEntities);
    }

    // Methods for ArrayCodec serialization
    public UUID[] getAttachedEntitiesArray() {
        return attachedEntities.toArray(new UUID[0]);
    }

    public void setAttachedEntitiesArray(UUID[] attachedEntitiesArray) {
        this.attachedEntities = new ArrayList<>();
        if (attachedEntitiesArray != null) {
            for (UUID uuid : attachedEntitiesArray) {
                if (uuid != null) {
                    this.attachedEntities.add(uuid);
                }
            }
        }
    }

    public void addAttachedEntity(UUID uuid) {
        this.attachedEntities.add(uuid);
    }

    public void clearAttachedEntities() {
        this.attachedEntities.clear();
    }

    public boolean hasAttachedEntities() {
        return !attachedEntities.isEmpty();
    }

    // Legacy compatibility methods
    @Deprecated
    @Nullable
    public UUID getAttachedEntity() {
        return attachedEntities.isEmpty() ? null : attachedEntities.get(0);
    }

    @Deprecated
    public void setAttachedEntity(@Nullable UUID attachedEntity) {
        this.attachedEntities.clear();
        if (attachedEntity != null) {
            this.attachedEntities.add(attachedEntity);
        }
    }

    @Deprecated
    public boolean hasAttachedEntity() {
        return !attachedEntities.isEmpty();
    }

    @Override
    public Component<ChunkStore> clone() {
        return new BoundPlantEntityComponent(this.attachedEntities);
    }
}
