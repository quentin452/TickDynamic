package com.wildex999.tickdynamic;

import com.google.common.collect.Maps;
import com.wildex999.tickdynamic.listinject.*;
import com.wildex999.tickdynamic.timemanager.ITimed;
import com.wildex999.tickdynamic.timemanager.TimedEntities;
import net.minecraft.profiler.Profiler;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.util.HashMap;
import java.util.List;

public class WorldEventHandler {
	private HashMap<World, ListManagerEntities> entityListManager;
	private HashMap<World, ListManager> tileListManager;

	public WorldEventHandler() {
		entityListManager = Maps.newHashMap();
		tileListManager = Maps.newHashMap();
	}

	@SubscribeEvent
	public void worldTickEvent(TickEvent.WorldTickEvent event) {
		Profiler profiler = event.world.profiler;
		if (!(profiler instanceof CustomProfiler))
			return;
		CustomProfiler customProfiler = (CustomProfiler) profiler;

		if (event.phase == Phase.START) {
			customProfiler.setStage(CustomProfiler.Stage.BeforeLoop);
		} else {
			customProfiler.setStage(CustomProfiler.Stage.None);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onDimensionLoad(WorldEvent.Load event) {
		if (event.getWorld().isRemote)
			return;

		//Register our own Entity List manager, copying over any existing Entities
		TickDynamicMod.logDebug("World load: " + event.getWorld().provider.getDimensionType().getName());

		//Inject Custom Profiler for watching Entity ticking
		try {
			setCustomProfiler(event.getWorld(), new CustomProfiler(event.getWorld().profiler));
		} catch (Exception e) {
			TickDynamicMod.logError("Unable to set TickDynamic World profiler! World will not be using TickDynamic: " + event.getWorld());
			e.printStackTrace();
			return; //Do not add TickDynamic to world
		}

		ListManagerEntities entityManager = new ListManagerEntities(event.getWorld());
		entityListManager.put(event.getWorld(), entityManager);
		ListManager tileEntityManager = new ListManager(event.getWorld(), EntityType.TileEntity);
		tileListManager.put(event.getWorld(), tileEntityManager);

		//Overwrite existing lists, copying any loaded Entities
		TickDynamicMod.logDebug("Adding " + event.getWorld().loadedEntityList.size() + " existing Entities.");
		List<? extends EntityObject> oldList = event.getWorld().loadedEntityList;
		ReflectionHelper.setPrivateValue(World.class, event.getWorld(), entityManager, "loadedEntityList", "field_72996_f");
		entityManager.addAll(oldList);

		//Tiles
		TickDynamicMod.logDebug("Adding " + event.getWorld().tickableTileEntities.size() + " existing TileEntities.");
		oldList = event.getWorld().tickableTileEntities;
		ReflectionHelper.setPrivateValue(World.class, event.getWorld(), tileEntityManager, "tickableTileEntities", "field_175730_i");
		tileEntityManager.addAll(oldList);

	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onDimensionUnload(WorldEvent.Unload event) {
		if (event.getWorld() == null || event.getWorld().isRemote)
			return;

		TickDynamicMod.logDebug("TickDynamic unloading injected lists for world: " + event.getWorld().provider.getDimensionType().getName());

		try {
			CustomProfiler customProfiler = (CustomProfiler) event.getWorld().profiler;
			setCustomProfiler(event.getWorld(), customProfiler.original);
		} catch (Exception e) {
			TickDynamicMod.logError("Failed to revert World Profiler to original");
			e.printStackTrace();
		}

		//Remove all references to the lists and EntityObjects contained(Groups will remain loaded in TickDynamic)
		ListManager list = entityListManager.remove(event.getWorld());
		if (list != null)
			list.clear();

		list = tileListManager.remove(event.getWorld());
		if (list != null)
			list.clear();

		//Clear loaded groups for world
		TickDynamicMod.instance.clearWorldEntityGroups(event.getWorld());

		//Clear timed groups
		ITimed manager = TickDynamicMod.instance.getWorldTimeManager(event.getWorld());
		if (manager != null)
			TickDynamicMod.instance.timedObjects.remove(manager);

		for (ITimed timed : TickDynamicMod.instance.timedObjects.values()) {
			if (timed instanceof TimedEntities) {
				TimedEntities timedGroup = (TimedEntities) timed;
				if (!timedGroup.getEntityGroup().valid)
					TickDynamicMod.instance.timedObjects.remove(timedGroup);
			}
		}

	}

	private void setCustomProfiler(World world, Profiler profiler) throws Exception {
		ReflectionHelper.setPrivateValue(World.class, world, profiler, "profiler", "field_71304_b");
	}
}
