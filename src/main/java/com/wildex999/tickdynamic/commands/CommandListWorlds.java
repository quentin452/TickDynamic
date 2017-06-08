package com.wildex999.tickdynamic.commands;

import com.wildex999.tickdynamic.TickDynamicMod;
import com.wildex999.tickdynamic.timemanager.TimeManager;
import com.wildex999.tickdynamic.timemanager.TimedGroup;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.WorldServer;
import org.apache.commons.lang3.StringUtils;

import java.text.DecimalFormat;
import java.util.List;

public class CommandListWorlds implements ICommand {

	private String formatCode = "\u00a7"; //Ignore this when counting length
	private int borderWidth;

	private int rowsPerPage;
	private int currentPage;
	private int maxPages;

	public CommandListWorlds() {
		borderWidth = 50;
		rowsPerPage = 6;
	}

	@Override
	public String getName() {
		return "tickdynamic listworlds";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "tickdynamic listworlds [page]";
	}

	@Override
	public List getAliases() {
		return null;
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
		if (args.length > 1 && args[1].equals("help")) {
			sender.sendMessage(new TextComponentString("Usage: " + getUsage(sender)));
			return;
		}

		StringBuilder outputBuilder = new StringBuilder();
		DecimalFormat decimalFormat = new DecimalFormat("#.00");

		currentPage = 1;
		maxPages = 0;

		//Get current page if set
		if (args.length == 2) {
			try {
				currentPage = Integer.parseInt(args[1]);
				if (currentPage <= 0) {
					sender.sendMessage(new TextComponentString(TextFormatting.RED + "Page number must be 1 and up, got: " + args[1]));
					currentPage = 1;
				}
			} catch (Exception e) {
				sender.sendMessage(new TextComponentString(TextFormatting.RED + "Expected a page number, got: " + args[1]));
				return;
			}
		}

		//Write header
		writeHeader(outputBuilder);

		int extraCount = 2; //External and Other
		int listSize = server.worlds.length + extraCount;
		if (listSize > rowsPerPage)
			maxPages = (int) Math.ceil(listSize / (float) rowsPerPage);
		else
			maxPages = 1;

		if (currentPage > maxPages)
			currentPage = maxPages;

		//Write data
		int toSkip = (currentPage - 1) * rowsPerPage;
		int toSend = rowsPerPage;
		for (WorldServer world : server.worlds) {
			//Skip for pages
			if (toSkip-- > 0)
				continue;
			if (toSend-- <= 0)
				break;

			TimeManager worldManager = TickDynamicMod.instance.getWorldTimeManager(world);
			if (world == null || worldManager == null)
				continue;

			outputBuilder.append(TextFormatting.GRAY + "| ").append(TextFormatting.RESET).append(world.provider.getDimensionType().getName());
			String usedTime = decimalFormat.format(worldManager.getTimeUsedAverage() / (double) TimeManager.timeMilisecond);
			String maxTime = decimalFormat.format(worldManager.getTimeMax() / (double) TimeManager.timeMilisecond);
			outputBuilder.append(TextFormatting.GRAY + " || ").append(TextFormatting.RESET).append(usedTime).append("/").append(maxTime);
			outputBuilder.append(TextFormatting.GRAY + " || ").append(TextFormatting.RESET).append(worldManager.getSliceMax()).append("\n");
		}

		if (currentPage == maxPages) {
			//Add Other
			TimedGroup other = TickDynamicMod.instance.getTimedGroup("other");
			if (other != null) {
				outputBuilder.append(TextFormatting.GRAY + "| ").append(TextFormatting.RESET + "(Other)");
				String usedTime = decimalFormat.format(other.getTimeUsedAverage() / (double) TimeManager.timeMilisecond);
				outputBuilder.append(TextFormatting.GRAY + " || ").append(TextFormatting.RESET).append(usedTime);
				outputBuilder.append(TextFormatting.GRAY + " || ").append(TextFormatting.RESET).append("N/A\n");
			}

			//Add External
			TimedGroup external = TickDynamicMod.instance.getTimedGroup("external");
			if (other != null) {
				outputBuilder.append(TextFormatting.GRAY + "| ").append(TextFormatting.RESET + "(External)");
				String usedTime = decimalFormat.format(external.getTimeUsedAverage() / (double) TimeManager.timeMilisecond);
				outputBuilder.append(TextFormatting.GRAY + " || ").append(TextFormatting.RESET).append(usedTime);
				outputBuilder.append(TextFormatting.GRAY + " || ").append(TextFormatting.RESET).append("N/A\n");
			}
		}

		writeFooter(outputBuilder);

		splitAndSend(sender, outputBuilder);
	}

	public void writeHeader(StringBuilder builder) {
		builder.append(TextFormatting.GREEN + "Worlds list with time. Usage: tickdynamic worldList [page]\n");

		builder.append(TextFormatting.GRAY + "+").append(StringUtils.repeat("=", borderWidth)).append("+\n");
		builder.append(TextFormatting.GRAY + "| ").append(TextFormatting.GOLD + "World").append(TextFormatting.GRAY);

		builder.append(" || ").append(TextFormatting.GOLD + "Time(Used/Allocated)").append(TextFormatting.GRAY);
		builder.append(" || ").append(TextFormatting.GOLD + "MaxSlices").append(TextFormatting.GRAY);
		builder.append("\n");
	}

	public void writeFooter(StringBuilder builder) {
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

	public void splitAndSend(ICommandSender sender, StringBuilder outputBuilder) {
		//Split newline and send
		String[] chatLines = outputBuilder.toString().split("\n");
		for (String chatLine : chatLines)
			sender.sendMessage(new TextComponentString(chatLine));
	}

	public int getVisibleLength(String str) {
		return (str.length() - (StringUtils.countMatches(str, formatCode) * 2));
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
