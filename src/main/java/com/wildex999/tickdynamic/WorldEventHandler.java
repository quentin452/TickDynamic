package com.wildex999.tickdynamic;

import java.util.HashMap;
import java.util.List;

import com.wildex999.tickdynamic.listinject.CustomProfiler;
import com.wildex999.tickdynamic.listinject.EntityObject;
import com.wildex999.tickdynamic.listinject.EntityType;
import com.wildex999.tickdynamic.listinject.ListManager;
import com.wildex999.tickdynamic.listinject.ListManagerEntities;
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

public class WorldEventHandler {
	public TickDynamicMod mod;
	
	private HashMap<World, ListManagerEntities> entityListManager;
	private HashMap<World, ListManager> tileListManager;
	
	public WorldEventHandler(TickDynamicMod mod) {
		this.mod = mod;
		entityListManager = new HashMap<World, ListManagerEntities>();
		tileListManager = new HashMap<World, ListManager>();
	}
	
    @SubscribeEvent
    public void worldTickEvent(TickEvent.WorldTickEvent event) {
		Profiler profiler = event.world.profiler;
		if(!(profiler instanceof CustomProfiler))
			return;
		CustomProfiler customProfiler = (CustomProfiler)profiler;
    	
    	if(event.phase == Phase.START) {
    		customProfiler.setStage(CustomProfiler.Stage.BeforeLoop);
    	}
    	else {
    		customProfiler.setStage(CustomProfiler.Stage.None);
    	}
    }
	
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDimensionLoad(WorldEvent.Load event)
    {
    	if(event.getWorld().isRemote)
    		return;
    	
    	//Register our own Entity List manager, copying over any existing Entities
    	if(mod.debug)
    		System.out.println("World load: " + event.getWorld().provider.getDimensionType().getName());
    	
    	//Inject Custom Profiler for watching Entity ticking
    	try {
    		setCustomProfiler(event.getWorld(), new CustomProfiler(event.getWorld().profiler));
    	} catch(Exception e) {
    		System.err.println("Unable to set TickDynamic World profiler! World will not be using TickDynamic: " + event.getWorld());
    		System.err.println(e);
    		return; //Do not add TickDynamic to world
    	}
    	
    	ListManagerEntities entityManager = new ListManagerEntities(event.getWorld(), mod);
    	entityListManager.put(event.getWorld(), entityManager);
    	ListManager tileEntityManager = new ListManager(event.getWorld(), mod, EntityType.TileEntity);
    	tileListManager.put(event.getWorld(), tileEntityManager);
    	
    	//Overwrite existing lists, copying any loaded Entities
    	if(mod.debug)
    		System.out.println("Adding " + event.getWorld().loadedEntityList.size() + " existing Entities.");
    	List<? extends EntityObject> oldList = event.getWorld().loadedEntityList;
    	ReflectionHelper.setPrivateValue(World.class, event.getWorld(), entityManager, "loadedEntityList", "field_72996_f");
    	for(EntityObject obj : oldList) {
    		entityManager.add(obj);
    	}
    	
    	//Tiles
    	if(mod.debug)
    		System.out.println("Adding " + event.getWorld().tickableTileEntities.size() + " existing TileEntities.");
    	oldList = event.getWorld().tickableTileEntities;
    	ReflectionHelper.setPrivateValue(World.class, event.getWorld(), tileEntityManager, "tickableTileEntities", "field_175730_i");
    	for(EntityObject obj : oldList) {
    		tileEntityManager.add(obj);
    	}
    	
    }
    
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDimensionUnload(WorldEvent.Unload event)
    {
    	if(event.getWorld() == null || event.getWorld().isRemote)
    		return;
    	
    	if(mod.debug)
    		System.out.println("TickDynamic unloading injected lists for world: " + event.getWorld().provider.getDimensionType().getName());
    	
    	try {
        	CustomProfiler customProfiler = (CustomProfiler)event.getWorld().profiler;
			setCustomProfiler(event.getWorld(), customProfiler.original);
		} catch (Exception e) {
			System.err.println("Failed to revert World Profiler to original");
			e.printStackTrace();
		}
    	
    	//Remove all references to the lists and EntityObjects contained(Groups will remain loaded in TickDynamic)
    	ListManager list = entityListManager.remove(event.getWorld());
    	if(list != null)
    		list.clear();
    	
    	list = tileListManager.remove(event.getWorld());
    	if(list != null)
    		list.clear();
    	
    	//Clear loaded groups for world
    	mod.clearWorldEntityGroups(event.getWorld());
    	
    	//Clear timed groups
    	ITimed manager = mod.getWorldTimeManager(event.getWorld());
    	if(manager != null)
    		mod.timedObjects.remove(manager);
    	
    	for(ITimed timed : mod.timedObjects.values())
		{
    		if(timed instanceof TimedEntities)
    		{
    			TimedEntities timedGroup = (TimedEntities)timed;
    			if(!timedGroup.getEntityGroup().valid)
    				mod.timedObjects.remove(timedGroup);
    		}
		}
    	
    }
    
    private void setCustomProfiler(World world, Profiler profiler) throws Exception {
    	ReflectionHelper.setPrivateValue(World.class, world, profiler, "profiler", "field_71304_b");
    }
}
