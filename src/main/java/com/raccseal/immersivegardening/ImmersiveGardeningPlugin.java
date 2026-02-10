package com.raccseal.immersivegardening;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.raccseal.immersivegardening.component.BoundPlantEntityComponent;
import com.raccseal.immersivegardening.component.PlantDisplayComponent;
import com.raccseal.immersivegardening.interaction.PlanterInteraction;
import com.raccseal.immersivegardening.system.PlanterSystems;

public class ImmersiveGardeningPlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static ImmersiveGardeningPlugin instance;

    // ChunkStore components (attached to block entities)
    private ComponentType<ChunkStore, BoundPlantEntityComponent> boundPlantEntityComponent;

    // EntityStore components (attached to display entities)
    private ComponentType<EntityStore, PlantDisplayComponent> plantDisplayComponent;

    public ImmersiveGardeningPlugin(JavaPluginInit init) {
        super(init);
        instance = this;
    }

    public static ImmersiveGardeningPlugin get() {
        return instance;
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("Setting up plugin " + this.getName());
        this.boundPlantEntityComponent = this.getChunkStoreRegistry().registerComponent(
                BoundPlantEntityComponent.class,
                "BoundPlantEntityComponent",
                BoundPlantEntityComponent.CODEC
        );
        this.plantDisplayComponent = this.getEntityStoreRegistry().registerComponent(
                PlantDisplayComponent.class,
                "PlantDisplayComponent",
                PlantDisplayComponent.CODEC
        );
        this.getCodecRegistry(Interaction.CODEC).register("PlanterInteraction", PlanterInteraction.class, PlanterInteraction.CODEC);

        // Register systems for handling planter block events
        ComponentRegistryProxy<EntityStore> entityRegistry = this.getEntityStoreRegistry();

        entityRegistry.registerSystem(new PlanterSystems.PlantDisplayTick());
        entityRegistry.registerSystem(new PlanterSystems.BreakPlanterSystem());

        LOGGER.atInfo().log(this.getName() + "plugin setup complete!");
    }


    public ComponentType<ChunkStore, BoundPlantEntityComponent> getBoundPlantEntityComponent() {
        return boundPlantEntityComponent;
    }

    public ComponentType<EntityStore, PlantDisplayComponent> getPlantDisplayComponent() {
        return plantDisplayComponent;
    }
}
