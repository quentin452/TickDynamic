package com.wildex999.tickdynamic.commands;

import java.text.DecimalFormat;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.mojang.realmsclient.gui.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import org.apache.commons.lang3.StringUtils;

import com.wildex999.tickdynamic.TickDynamicMod;
import com.wildex999.tickdynamic.commands.CommandHandler.SubCommands;
import com.wildex999.tickdynamic.timemanager.ITimed;
import com.wildex999.tickdynamic.timemanager.TimeManager;
import com.wildex999.tickdynamic.timemanager.TimedGroup;
import com.wildex999.tickdynamic.timemanager.TimedEntities;

import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.world.WorldServer;

public class CommandListWorlds implements ICommand {

	private TickDynamicMod mod;
	
	private String formatCode = "\u00a7"; //Ignore this when counting length
	private int borderWidth;
	
	private int rowsPerPage;
	private int currentPage;
	private int maxPages;
	
	private class ListData {
		public String worldName;
		public String worldData;
		public String tileData;
		public String entityData;
	}
	
	public CommandListWorlds(TickDynamicMod mod) {
		this.mod = mod;
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
		if(args.length > 1 && args[1].equals("help"))
		{
			sender.sendMessage(new TextComponentString("Usage: " + getUsage(sender)));
			return;
		}
		
		StringBuilder outputBuilder = new StringBuilder();
		DecimalFormat decimalFormat = new DecimalFormat("#.00");
		
		currentPage = 1;
		maxPages = 0;
		
		//Get current page if set
		if(args.length == 2)
		{
			try {
			currentPage = Integer.parseInt(args[1]);
			if(currentPage <= 0)
			{
				sender.sendMessage(new TextComponentString(ChatFormatting.RED + "Page number must be 1 and up, got: " + args[1]));
				currentPage = 1;
			}
			} catch(Exception e) {
				sender.sendMessage(new TextComponentString(ChatFormatting.RED + "Expected a page number, got: " + args[1]));
				return;
			}
		}
		
		//Write header
		writeHeader(outputBuilder);
		
		int extraCount = 2; //External and Other
		int listSize = mod.server.worlds.length + extraCount;
		if(listSize > rowsPerPage)
			maxPages = (int)Math.ceil(listSize/(float)rowsPerPage);
		else
			maxPages = 1;
		
		if(currentPage > maxPages)
			currentPage = maxPages;
		
		//Write data
		int toSkip = (currentPage-1) * rowsPerPage;
		int toSend = rowsPerPage;
		for(WorldServer world : mod.server.worlds) {
			//Skip for pages
			if(toSkip-- > 0)
				continue;
			if(toSend-- <= 0)
				break;
			
			TimeManager worldManager = mod.getWorldTimeManager(world);
			if(world == null || worldManager == null)
				continue;
			
			outputBuilder.append(ChatFormatting.GRAY + "| ").append(ChatFormatting.RESET + world.provider.getDimensionType().getName());
			String usedTime = decimalFormat.format(worldManager.getTimeUsedAverage()/(double)TimeManager.timeMilisecond);
			String maxTime = decimalFormat.format(worldManager.getTimeMax()/(double)TimeManager.timeMilisecond);
			outputBuilder.append(ChatFormatting.GRAY + " || ").append(ChatFormatting.RESET).append(usedTime).append("/").append(maxTime);
			outputBuilder.append(ChatFormatting.GRAY + " || ").append(ChatFormatting.RESET).append(worldManager.getSliceMax()).append("\n");
		}
		
		if(currentPage == maxPages)
		{
			//Add Other
			TimedGroup other = mod.getTimedGroup("other");
			if(other != null)
			{
				outputBuilder.append(ChatFormatting.GRAY + "| ").append(ChatFormatting.RESET + "(Other)");
				String usedTime = decimalFormat.format(other.getTimeUsedAverage()/(double)TimeManager.timeMilisecond);
				outputBuilder.append(ChatFormatting.GRAY + " || ").append(ChatFormatting.RESET).append(usedTime);
				outputBuilder.append(ChatFormatting.GRAY + " || ").append(ChatFormatting.RESET).append("N/A\n");
			}
			
			//Add External
			TimedGroup external = mod.getTimedGroup("external");
			if(other != null)
			{
				outputBuilder.append(ChatFormatting.GRAY + "| ").append(ChatFormatting.RESET + "(External)");
				String usedTime = decimalFormat.format(external.getTimeUsedAverage()/(double)TimeManager.timeMilisecond);
				outputBuilder.append(ChatFormatting.GRAY + " || ").append(ChatFormatting.RESET).append(usedTime);
				outputBuilder.append(ChatFormatting.GRAY + " || ").append(ChatFormatting.RESET).append("N/A\n");
			}
		}
		
		writeFooter(outputBuilder);
		
		splitAndSend(sender, outputBuilder);
	}
	
	public void writeHeader(StringBuilder builder) {
		builder.append(ChatFormatting.GREEN + "Worlds list with time. Usage: tickdynamic worldList [page]\n");
		
		builder.append(ChatFormatting.GRAY + "+" + StringUtils.repeat("=", borderWidth) + "+\n");
		builder.append(ChatFormatting.GRAY + "| ").append(ChatFormatting.GOLD + "World").append(ChatFormatting.GRAY);
		
		builder.append(" || " ).append(ChatFormatting.GOLD + "Time(Used/Allocated)").append(ChatFormatting.GRAY);
		builder.append(" || " ).append(ChatFormatting.GOLD + "MaxSlices").append(ChatFormatting.GRAY);
		builder.append("\n");
	}
	
	public void writeFooter(StringBuilder builder) {
		if(maxPages == 0)
			builder.append(ChatFormatting.GRAY + "+" + StringUtils.repeat("=", borderWidth) + "+\n");
		else
		{
			String pagesStr = ChatFormatting.GREEN + "Page " + currentPage + "/" + maxPages;
			int pagesLength = getVisibleLength(pagesStr);
			int otherLength = borderWidth - pagesLength;
			builder.append(ChatFormatting.GRAY + "+" + StringUtils.repeat("=", otherLength/2));
			builder.append(pagesStr);
			builder.append(ChatFormatting.GRAY + StringUtils.repeat("=", otherLength/2) + "+\n");
		}
	}
	
	public void splitAndSend(ICommandSender sender, StringBuilder outputBuilder) {
		//Split newline and send
		String[] chatLines = outputBuilder.toString().split("\n");
		for(String chatLine : chatLines)
			sender.sendMessage(new TextComponentString(chatLine));
	}
	
	public int getVisibleLength(String str) {
		return (str.length() - (StringUtils.countMatches(str, formatCode)*2));
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
