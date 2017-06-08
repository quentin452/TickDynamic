package com.wildex999.tickdynamic.commands;

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
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import org.apache.commons.lang3.StringUtils;

import java.text.DecimalFormat;
import java.util.*;

public class CommandWorld implements ICommand {
	private int borderWidth;
	private World world;

	private int rowsPerPage = 6;
	private int currentPage;
	private int maxPages;

	private final DecimalFormat decimalFormat = new DecimalFormat("#.00");
	private final String formatCode = "\u00a7"; //Ignore this when counting length

	public CommandWorld() {
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
					sender.sendMessage(new TextComponentString(TextFormatting.RED + "Page number must be 1 and up, got: " + args[2]));
					currentPage = 1;
				}
			} catch (Exception e) {
				sender.sendMessage(new TextComponentString(TextFormatting.RED + "Expected a page number, got: " + args[2]));
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
			sender.sendMessage(new TextComponentString(TextFormatting.RED + "Expected a world dimension(Ex: dim0 or just 0), got: " + worldDimStr));
			return;
		}
		world = DimensionManager.getWorld(worldDim);

		if (world == null) {
			sender.sendMessage(new TextComponentString(TextFormatting.RED + "No world with dimension id: " + worldDimStr));
			return;
		}

		writeHeader(outputBuilder);

		//Get groups from world
		List<EntityGroup> groups = new ArrayList<>();
		if (world.loadedEntityList instanceof ListManager)
			addGroupsFromList(groups, (ListManager) world.loadedEntityList);
		if (world.tickableTileEntities instanceof ListManager)
			addGroupsFromList(groups, (ListManager) world.tickableTileEntities);

		//Sort the groups, so we don't just have Entities followed by TileEntities
		//TODO: More sorting options
		groups.sort((o1, o2) -> {
			EntityGroup group1 = (EntityGroup) o1;
			EntityGroup group2 = (EntityGroup) o2;
			return group1.getName().compareTo(group2.getName());
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
		builder.append(TextFormatting.GREEN + "Groups for world: ").append(TextFormatting.RESET).append(world.provider.getDimensionType().getName()).
				append("(DIM: ").append(world.provider.getDimension()).append(")\n");

		builder.append(TextFormatting.GRAY + "+").append(StringUtils.repeat("=", borderWidth)).append("+\n");
		builder.append(TextFormatting.GRAY + "| ").append(TextFormatting.GOLD + "Group").append(TextFormatting.GRAY);

		builder.append(" || ").append(TextFormatting.GOLD + "Time(Avg.)").append(TextFormatting.GRAY);
		builder.append(" || ").append(TextFormatting.GOLD + "EntitiesRun(Avg.)").append(TextFormatting.GRAY);
		builder.append(" || ").append(TextFormatting.GOLD + "MaxSlices").append(TextFormatting.GRAY);
		builder.append(" || ").append(TextFormatting.GOLD + "TPS(Avg.)").append(TextFormatting.GRAY);
		builder.append("\n");
	}

	private void writeGroup(StringBuilder builder, EntityGroup group) {
		TimedEntities timedGroup = group.timedGroup;
		builder.append(TextFormatting.GRAY + "| ").append(TextFormatting.RESET).append(group.getName());

		if (timedGroup == null) { //No Timed data
			builder.append(TextFormatting.RED + " N/A\n");
			return;
		}

		String usedTime = decimalFormat.format(timedGroup.getTimeUsedAverage() / (double) TimeManager.timeMilisecond);
		String maxTime = decimalFormat.format(timedGroup.getTimeMax() / (double) TimeManager.timeMilisecond);
		builder.append(TextFormatting.GRAY + " || ").append(TextFormatting.RESET).append(usedTime).append("/").append(maxTime);

		int runObjects = timedGroup.getObjectsRunAverage();
		int countObjects = group.entities.size();
		builder.append(TextFormatting.GRAY + " || ").append(TextFormatting.RESET).append(runObjects).append("/").append(countObjects);
		builder.append(TextFormatting.GRAY + " || ").append(TextFormatting.RESET).append(timedGroup.getSliceMax());

		//TPS coloring
		String color;
		if (timedGroup.averageTPS >= 19)
			color = TextFormatting.GREEN.toString();
		else if (timedGroup.averageTPS > 10)
			color = TextFormatting.YELLOW.toString();
		else
			color = TextFormatting.RED.toString();
		builder.append(TextFormatting.GRAY + " || ").append(color).append(decimalFormat.format(timedGroup.averageTPS)).append(TextFormatting.RESET + "TPS");

		builder.append("\n");
	}

	private void writeFooter(StringBuilder builder) {
		if (maxPages == 0)
			builder.append(TextFormatting.GRAY + "+").append(StringUtils.repeat("=", borderWidth)).append("+\n");
		else {
			String pagesStr = TextFormatting.GREEN + "Page " + currentPage + "/" + maxPages;
			int pagesLength = getVisibleLength(pagesStr);
			int otherLength = borderWidth - pagesLength;
			builder.append(TextFormatting.GRAY + "+").append(StringUtils.repeat("=", otherLength / 2));
			builder.append(pagesStr);
			builder.append(TextFormatting.GRAY).append(StringUtils.repeat("=", otherLength / 2)).append("+\n");
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
