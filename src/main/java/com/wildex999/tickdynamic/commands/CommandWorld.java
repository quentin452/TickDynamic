package com.wildex999.tickdynamic.commands;

import com.mojang.realmsclient.gui.ChatFormatting;
import com.wildex999.tickdynamic.TickDynamicMod;
import com.wildex999.tickdynamic.listinject.EntityGroup;
import com.wildex999.tickdynamic.listinject.ListManager;
import com.wildex999.tickdynamic.timemanager.TimeManager;
import com.wildex999.tickdynamic.timemanager.TimedEntities;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import org.apache.commons.lang3.StringUtils;

import java.text.DecimalFormat;
import java.util.*;

public class CommandWorld implements ICommand {

	private TickDynamicMod mod;
	private int borderWidth;
	private World world;

	private int rowsPerPage = 6;
	private int currentPage;
	private int maxPages;

	private final DecimalFormat decimalFormat = new DecimalFormat("#.00");
	private final String formatCode = "\u00a7"; //Ignore this when counting length

	public CommandWorld(TickDynamicMod mod) {
		this.mod = mod;
		this.borderWidth = 50;
	}

	@Override
	public String getName() {
		return "tickdynamic world";
	}

	@Override
	public String getUsage(ICommandSender p_71518_1_) {
		return "tickdynamic world (world dim) [page]";
	}

	@Override
	public List getAliases() {
		return null;
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
		if (args.length <= 1) {
			sender.sendMessage(new TextComponentString("Usage: " + getUsage(sender)));
			return;
		}

		StringBuilder outputBuilder = new StringBuilder();
		currentPage = 1;
		maxPages = 0;

		//Get current page if set
		if (args.length == 3) {
			try {
				currentPage = Integer.parseInt(args[2]);
				if (currentPage <= 0) {
					sender.sendMessage(new TextComponentString(ChatFormatting.RED + "Page number must be 1 and up, got: " + args[2]));
					currentPage = 1;
				}
			} catch (Exception e) {
				sender.sendMessage(new TextComponentString(ChatFormatting.RED + "Expected a page number, got: " + args[2]));
				return;
			}
		}

		//Get world by dim
		//TODO: Allow passing in world name(And add auto-complete for it)
		String worldDimStr = args[1];
		int worldDim;
		if (worldDimStr.startsWith("dim"))
			worldDimStr = worldDimStr.substring(3);
		try {
			worldDim = Integer.parseInt(worldDimStr);
		} catch (Exception e) {
			sender.sendMessage(new TextComponentString(ChatFormatting.RED + "Expected a world dimension(Ex: dim0 or just 0), got: " + worldDimStr));
			return;
		}
		world = DimensionManager.getWorld(worldDim);

		if (world == null) {
			sender.sendMessage(new TextComponentString(ChatFormatting.RED + "No world with dimension id: " + worldDimStr));
			return;
		}

		writeHeader(outputBuilder);

		//Get groups from world
		List<EntityGroup> groups = new ArrayList<EntityGroup>();
		if (world.loadedEntityList instanceof ListManager)
			addGroupsFromList(groups, (ListManager) world.loadedEntityList);
		if (world.tickableTileEntities instanceof ListManager)
			addGroupsFromList(groups, (ListManager) world.tickableTileEntities);

		//Sort the groups, so we don't just have Entities followed by TileEntities
		//TODO: More sorting options
		Collections.sort(groups, new Comparator() {
			public int compare(Object o1, Object o2) {
				EntityGroup group1 = (EntityGroup) o1;
				EntityGroup group2 = (EntityGroup) o2;
				return group1.getName().compareTo(group2.getName());
			}
		});

		int listSize = groups.size();
		if (listSize > rowsPerPage)
			maxPages = (int) Math.ceil(listSize / (float) rowsPerPage);
		else
			maxPages = 1;

		if (currentPage > maxPages)
			currentPage = maxPages;

		//Write stats
		int toSkip = (currentPage - 1) * rowsPerPage;
		int toSend = rowsPerPage;
		for (EntityGroup group : groups) {
			//Skip for pages
			if (toSkip-- > 0)
				continue;
			if (toSend-- <= 0)
				break;

			writeGroup(outputBuilder, group);
		}

		writeFooter(outputBuilder);

		splitAndSend(sender, outputBuilder);
	}

	private void addGroupsFromList(List<EntityGroup> targetList, ListManager worldList) {
		Iterator<EntityGroup> it = worldList.getGroupIterator();
		while (it.hasNext())
			targetList.add(it.next());
	}

	private void writeHeader(StringBuilder builder) {
		builder.append(ChatFormatting.GREEN + "Groups for world: ").append(ChatFormatting.RESET + world.provider.getDimensionType().getName()).
				append("(DIM: ").append(world.provider.getDimension()).append(")\n");

		builder.append(ChatFormatting.GRAY + "+" + StringUtils.repeat("=", borderWidth) + "+\n");
		builder.append(ChatFormatting.GRAY + "| ").append(ChatFormatting.GOLD + "Group").append(ChatFormatting.GRAY);

		builder.append(" || ").append(ChatFormatting.GOLD + "Time(Avg.)").append(ChatFormatting.GRAY);
		builder.append(" || ").append(ChatFormatting.GOLD + "EntitiesRun(Avg.)").append(ChatFormatting.GRAY);
		builder.append(" || ").append(ChatFormatting.GOLD + "MaxSlices").append(ChatFormatting.GRAY);
		builder.append(" || ").append(ChatFormatting.GOLD + "TPS(Avg.)").append(ChatFormatting.GRAY);
		builder.append("\n");
	}

	private void writeGroup(StringBuilder builder, EntityGroup group) {
		TimedEntities timedGroup = group.timedGroup;
		builder.append(ChatFormatting.GRAY + "| ").append(ChatFormatting.RESET + group.getName());

		if (timedGroup == null) { //No Timed data
			builder.append(ChatFormatting.RED + " N/A\n");
			return;
		}

		String usedTime = decimalFormat.format(timedGroup.getTimeUsedAverage() / (double) TimeManager.timeMilisecond);
		String maxTime = decimalFormat.format(timedGroup.getTimeMax() / (double) TimeManager.timeMilisecond);
		builder.append(ChatFormatting.GRAY + " || ").append(ChatFormatting.RESET).append(usedTime).append("/").append(maxTime);

		int runObjects = timedGroup.getObjectsRunAverage();
		int countObjects = group.entities.size();
		builder.append(ChatFormatting.GRAY + " || ").append(ChatFormatting.RESET).append(runObjects).append("/").append(countObjects);
		builder.append(ChatFormatting.GRAY + " || ").append(ChatFormatting.RESET).append(timedGroup.getSliceMax());

		//TPS coloring
		String color;
		if (timedGroup.averageTPS >= 19)
			color = ChatFormatting.GREEN.toString();
		else if (timedGroup.averageTPS > 10)
			color = ChatFormatting.YELLOW.toString();
		else
			color = ChatFormatting.RED.toString();
		builder.append(ChatFormatting.GRAY + " || ").append(color).append(decimalFormat.format(timedGroup.averageTPS)).append(ChatFormatting.RESET + "TPS");

		builder.append("\n");
	}

	private void writeFooter(StringBuilder builder) {
		if (maxPages == 0)
			builder.append(ChatFormatting.GRAY + "+" + StringUtils.repeat("=", borderWidth) + "+\n");
		else {
			String pagesStr = ChatFormatting.GREEN + "Page " + currentPage + "/" + maxPages;
			int pagesLength = getVisibleLength(pagesStr);
			int otherLength = borderWidth - pagesLength;
			builder.append(ChatFormatting.GRAY + "+" + StringUtils.repeat("=", otherLength / 2));
			builder.append(pagesStr);
			builder.append(ChatFormatting.GRAY + StringUtils.repeat("=", otherLength / 2) + "+\n");
		}
	}

	public int getVisibleLength(String str) {
		return (str.length() - (StringUtils.countMatches(str, formatCode) * 2));
	}

	public void splitAndSend(ICommandSender sender, StringBuilder outputBuilder) {
		//Split newline and send
		String[] chatLines = outputBuilder.toString().split("\n");
		for (String chatLine : chatLines)
			sender.sendMessage(new TextComponentString(chatLine));
	}

	@Override
	public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
		return sender.canUseCommand(1, getName());
	}

	@Override
	public List getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
		return null;
	}

	@Override
	public boolean isUsernameIndex(String[] p_82358_1_, int p_82358_2_) {
		return false;
	}

	@Override
	public int compareTo(ICommand o) {
		return 0;
	}

}
