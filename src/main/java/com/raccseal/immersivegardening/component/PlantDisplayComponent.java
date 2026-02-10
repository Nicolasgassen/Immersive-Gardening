package com.raccseal.immersivegardening.component;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.Nullable;

/**
 * Component attached to the display entity that holds the plant visual.
 * This stores the held item and the position of the planter block.
 */
public class PlantDisplayComponent implements Component<EntityStore> {
    public static final BuilderCodec<PlantDisplayComponent> CODEC = BuilderCodec.builder(PlantDisplayComponent.class, PlantDisplayComponent::new)
            .append(new KeyedCodec<>("HeldStack", ItemStack.CODEC),
                    PlantDisplayComponent::setHeldStack,
                    PlantDisplayComponent::getHeldStack)
            .add()
            .append(new KeyedCodec<>("PlanterPosition", Vector3i.CODEC),
                    PlantDisplayComponent::setPlanterPosition,
                    PlantDisplayComponent::getPlanterPosition)
            .add()
            .build();

    @Nullable
    private ItemStack heldStack;

    @Nullable
    private Vector3i planterPosition;

    public PlantDisplayComponent() {
        this.heldStack = null;
        this.planterPosition = null;
    }

    public PlantDisplayComponent(@Nullable ItemStack heldStack, @Nullable Vector3i planterPosition) {
        this.heldStack = heldStack;
        this.planterPosition = planterPosition;
    }

    @Nullable
    public ItemStack getHeldStack() {
        return heldStack;
    }

    public void setHeldStack(@Nullable ItemStack heldStack) {
        this.heldStack = heldStack;
    }

    @Nullable
    public Vector3i getPlanterPosition() {
        return planterPosition;
    }

    public void setPlanterPosition(@Nullable Vector3i planterPosition) {
        this.planterPosition = planterPosition;
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @Override
    public Component<EntityStore> clone() {
        return new PlantDisplayComponent(this.heldStack, this.planterPosition);
    }
}
