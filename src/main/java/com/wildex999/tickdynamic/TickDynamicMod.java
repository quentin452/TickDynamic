package com.wildex999.tickdynamic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.Semaphore;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.wildex999.tickdynamic.commands.CommandHandler;
import com.wildex999.tickdynamic.listinject.EntityGroup;
import com.wildex999.tickdynamic.listinject.EntityType;
import com.wildex999.tickdynamic.timemanager.ITimed;
import com.wildex999.tickdynamic.timemanager.TimeManager;
import com.wildex999.tickdynamic.timemanager.TimedEntities;
import com.wildex999.tickdynamic.timemanager.TimedGroup;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.ServerTickEvent;
import org.apache.logging.log4j.Level;

/**
 * @author Wildex999 ( wildex999@gmail.com )
 * @author The_Fireplace ( the.f1repl4ce@gmail.com )
 * Later ideas:
 * - Entities far away from players tick less often.
 * - Entities and TileEntities grouped by owner(Player), and limits can be set per player.
 */

@Mod(modid = TickDynamicMod.MODID, name = TickDynamicMod.MODNAME, version = TickDynamicMod.VERSION, updateJSON = "https://bitbucket.org/The_Fireplace/minecraft-mod-updates/raw/master/tickdynamic.json")
public class TickDynamicMod {
	public static final String MODID = "tickdynamic";
	public static final String MODNAME = "Tick Dynamic";
	public static final String VERSION = "${version}";
	public static boolean debugGroups = false;
	public static boolean debugTimer = false;
	@Mod.Instance(MODID)
	public static TickDynamicMod instance;


	public Map<String, ITimed> timedObjects;
	public Map<String, EntityGroup> entityGroups;
	public TimeManager root;
	public boolean enabled;
	public MinecraftServer server;

	public Semaphore tpsMutex;
	public Timer tpsTimer;
	public int tickCounter;
	public double averageTPS;
	public int tpsAverageSeconds = 5; //Seconds to average TPS over
	public LinkedList<Integer> tpsList; //List of latest TPS for calculating average

	//Config
	public Configuration config;
	public boolean saveConfig;

	public int defaultTickTime = 50;
	public int defaultEntitySlicesMax = 100;
	public int defaultEntityMinimumObjects = 100;
	public float defaultEntityMinimumTPS = 0;
	public float defaultEntityMinimumTime = 0;
	public int defaultWorldSlicesMax = 100;
	public int defaultAverageTicks = 20;

	@Mod.EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		tpsMutex = new Semaphore(1);
		tpsTimer = new Timer();
		tpsList = Lists.newLinkedList();
		config = new Configuration(event.getSuggestedConfigurationFile());
	}

	//Load the configuration file
	//groups: Whether to (re)load the groups
	public void loadConfig(boolean groups) {
		//TODO: Separate Initial load, reload and write
		TickDynamicConfig.loadConfig(this, groups);
	}

	public void writeConfig(boolean saveFile) {
		//TODO
	}

	//Queue to save any changes done to the config
	public void queueSaveConfig() {
		saveConfig = true;
	}

	@Mod.EventHandler
	public void init(FMLInitializationEvent event) {
		MinecraftForge.EVENT_BUS.register(this);
		timedObjects = Maps.newHashMap();
		entityGroups = Maps.newHashMap();

		loadConfig(true);

		root = new TimeManager(null, "root", null);
		root.init();
		root.setTimeMax(defaultTickTime * TimeManager.timeMilisecond);

		//Other group accounts the time used in a tick, but not for Entities or TileEntities
		TimedGroup otherTimed = new TimedGroup(null, "other", "other");
		otherTimed.setSliceMax(0); //Make it get unlimited time
		root.addChild(otherTimed);

		//External group accounts the time used between ticks due to external load
		TimedGroup externalTimed = new TimedGroup(null, "external", "external");
		externalTimed.setSliceMax(0);
		root.addChild(externalTimed);

		MinecraftForge.EVENT_BUS.register(new WorldEventHandler());
	}


	@Mod.EventHandler
	public void serverStart(FMLServerStartingEvent event) {
		event.registerServerCommand(new CommandHandler());

		tpsTimer.schedule(new TimerTickTask(), 1000, 1000);

		server = event.getServer();
	}

	@Mod.EventHandler
	public void serverStop(FMLServerStoppingEvent event) {
		tpsTimer.cancel();
		server = null;
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void tickEventStart(ServerTickEvent event) {
		if (event.phase == Phase.START) {
			TimedGroup externalGroup = getTimedGroup("external");
			externalGroup.endTimer();

			//Set the correct externalGroup time
			long msPerTick = 50;
			long overTime = externalGroup.getTimeUsed() - (msPerTick * externalGroup.timeMilisecond); //overTime = time used above given tick time
			long overTimeTick = (msPerTick * externalGroup.timeMilisecond) - (root.getTimeUsed() - externalGroup.getTimeUsed());
			if (overTimeTick < 0)
				overTime += overTimeTick;
			/*System.out.println("TickTime: " + ((root.getTimeUsed()-externalGroup.getTimeUsed())/(double)externalGroup.timeMilisecond) +
    				" Full Tick time: " + (externalGroup.getTimeUsed()/(double)externalGroup.timeMilisecond) +
    				" External time used: " + (overTime/(double)externalGroup.timeMilisecond)+"ms");*/
			if (overTime < 0)
				externalGroup.setTimeUsed(0);
			else
				externalGroup.setTimeUsed(overTime);

			externalGroup.startTimer();

			//Clear any values from the previous tick for all worlds.
			root.newTick(true);

			getTimedGroup("other").startTimer();
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void tickEventEnd(ServerTickEvent event) {
		if (event.phase == Phase.END) {
			getTimedGroup("other").endTimer();
			root.endTick(true);

			if (debugTimer)
				logTrace("Tick time used: " + (root.getTimeUsed() / TimeManager.timeMilisecond) + "ms");

			//After every world is done ticking, re-balance the time slices according
			//to the data gathered during the tick.
			root.balanceTime();

			//Calculate TPS
			updateTPS();

			if (saveConfig) {
				saveConfig = false;
				config.save();
			}
		}
	}

	//Calculate the new average TPS
	//Note: acquires a mutex due to contention with timer thread on tickCounter and tpsList.
	public void updateTPS() {
		try {
			tpsMutex.acquire();
			tickCounter++;

			//Calculate average from list
			averageTPS = 0;
			for (int tps : tpsList) {
				averageTPS += tps;
			}
			averageTPS = averageTPS / tpsList.size();

			tpsMutex.release();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	//Get the named TimedGroup.
	//Return: Null if not loaded
	public TimedGroup getTimedGroup(String name) {
		return (TimedGroup) timedObjects.get(name);
	}

	//Get a named EntityGroup which possibly does not belong to a world
	//Return: Null if doesn't exist in config
	public EntityGroup getEntityGroup(String name) {
		//All Global Groups are loaded during config load/reload

		return entityGroups.get(name);
	}

	public TimeManager getTimeManager(String name) {
		return (TimeManager) timedObjects.get(name);
	}

	private String getEntityGroupName(World world, String name) {
		String remote = "";
		if (world.isRemote)
			remote = "client_";
		StringBuilder strBuilder = new StringBuilder().append("worlds.").append(remote).append("dim").append(world.provider.getDimension());
		if (name != null && name.length() > 0)
			strBuilder.append(".").append(name);

		return strBuilder.toString();
	}

	//Get the TimeManager for a world.
	//Will create if it doesn't exist.
	public TimeManager getWorldTimeManager(World world) {
		String managerName = getEntityGroupName(world, null);
		TimeManager worldManager = getTimeManager(managerName);

		if (worldManager == null) {
			worldManager = new TimeManager(world, managerName, managerName);
			worldManager.init();
			if (world.isRemote)
				worldManager.setSliceMax(0);

			config.setCategoryComment(managerName, world.provider.getDimensionType().getName());

			root.addChild(worldManager);
		}

		return worldManager;
	}

	//Get the named TimedGroup from the given world.
	//canCreate: Whether to create if it does not exist
	//hasConfig: Whether to create with config entry
	public TimedEntities getWorldTimedGroup(World world, String name, boolean canCreate, boolean hasConfig) {
		String groupName = getEntityGroupName(world, name);
		TimedGroup group = getTimedGroup(groupName);

		if ((group == null || !(group instanceof TimedEntities)) && canCreate) {
			String baseGroupName = "groups." + name;
			TimedGroup baseGroup = getTimedGroup(baseGroupName);
			group = new TimedEntities(world, name, hasConfig ? groupName : null, baseGroup);
			group.init();

			TimeManager worldManager = getWorldTimeManager(world);
			worldManager.addChild(group);
		}

		return (TimedEntities) group;
	}

	//groupType: The type to make the new Group if it doesn't already exist
	//Will return existing group even if type doesn't match.
	//canCreate: Whether to create the group if it does not exist
	//hasConfig: Whether to create with config entry
	public EntityGroup getWorldEntityGroup(World world, String name, EntityType groupType, boolean canCreate, boolean hasConfig) {
		String groupName = getEntityGroupName(world, name);
		EntityGroup group = getEntityGroup(groupName);

		if (group == null && canCreate) //Create group for world
		{
			String baseGroupName = "groups." + name;
			EntityGroup baseGroup = getEntityGroup(baseGroupName);
			group = new EntityGroup(world, getWorldTimedGroup(world, name, true, hasConfig), name, hasConfig ? groupName : null, groupType, baseGroup);
			entityGroups.put(groupName, group);
		}

		return group;
	}

	//Get all EntityGroups for the given world
	public List<EntityGroup> getWorldEntityGroups(World world) {
		List<EntityGroup> groups = new ArrayList<>();

		String remote = "";
		int offsetCount = 10;
		if (world.isRemote) {
			remote = "client_";
			offsetCount += 7;
		}

		String groupNamePrefix = String.valueOf(world.provider.getDimension()) + ".";
		//TODO: Don't compare the first 10 characters, as they are always the same(Have offset)
		for (Map.Entry<String, EntityGroup> entry : entityGroups.entrySet()) {
			String groupName = entry.getKey();
			if (!groupName.startsWith(groupNamePrefix, offsetCount))
				continue;

			groups.add(entry.getValue());
		}

		return groups;
	}

	//Unload every Entity Group for the given world
	public void clearWorldEntityGroups(World world) {
		if (world == null)
			return;

		List<EntityGroup> groups = getWorldEntityGroups(world);
		int groupCount = 0;
		for (EntityGroup group : groups) {
			if (group.getWorld() == null) {
				logDebug("Unable to unload group: " + group.getName() + ". World is null.");
				continue;
			}
			String groupName = getEntityGroupName(group.getWorld(), group.getName());
			if (!entityGroups.remove(groupName, group)) {
				logError("Failed to unload EntityGroup: " + groupName + " for world: " + world.provider.getDimensionType().getName());
				logError("This might cause the world to remain in memory!");
			} else
				groupCount++;
			group.valid = false;
		}

		logDebug("Unloaded " + groupCount + " EntityGroups while unloading world: " + world.provider.getDimensionType().getName());
	}

	public String getWorldPrefix(World world) {
		return "worlds.dim" + world.provider.getDimension();
	}

	public ConfigCategory getWorldConfigCategory(World world) {
		return config.getCategory(getWorldPrefix(world));
	}

	//Get the group for Ungrouped Tile Entities in the given world
	//Will create the world TimeManager and Entity Group if it doesn't exist.
	public EntityGroup getWorldTileEntities(World world) {
		return getWorldEntityGroup(world, "tileentity", EntityType.TileEntity, true, true);
	}

	//Get the group for Ungrouped Tile Entities in the given world
	//Will create the world TimeManager and Entity Group if it doesn't exist.
	public EntityGroup getWorldEntities(World world) {
		return getWorldEntityGroup(world, "entity", EntityType.Entity, true, true);
	}

	public static void logInfo(String log, Object... params) {
		FMLLog.log(MODNAME, Level.INFO, log, params);
	}

	public static void logDebug(String log, Object... params) {
		FMLLog.log(MODNAME, Level.DEBUG, log, params);
	}

	public static void logError(String log, Object... params) {
		FMLLog.log(MODNAME, Level.ERROR, log, params);
	}

	public static void logTrace(String log, Object... params) {
		FMLLog.log(MODNAME, Level.TRACE, log, params);
	}

	public static void logWarn(String log, Object... params) {
		FMLLog.log(MODNAME, Level.WARN, log, params);
	}
}
