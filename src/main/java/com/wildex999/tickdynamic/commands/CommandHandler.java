package com.wildex999.tickdynamic.commands;

import com.wildex999.tickdynamic.TickDynamicMod;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.text.DecimalFormat;
import java.util.*;

public class CommandHandler implements ICommand {

	private List<String> aliases;
	private Map<String, ICommand> subCommandHandlers;
	private String listSubCommands;

	public enum SubCommands {
		tps,
		listworlds,
		world,
		identify,
		reload,
		reloadgroups,
		enabled,
		help
	}

	public CommandHandler() {

		aliases = new ArrayList<>();
		aliases.add("tickdynamic");
		aliases.add("td");

		subCommandHandlers = new HashMap<>();
		subCommandHandlers.put("reload", new CommandReload());
		subCommandHandlers.put("reloadgroups", new CommandReloadGroups());
		subCommandHandlers.put("listworlds", new CommandListWorlds());
		subCommandHandlers.put("world", new CommandWorld());
		subCommandHandlers.put("enabled", new CommandEnabled());

		StringBuilder builderSubCommands = new StringBuilder();
		SubCommands[] subs = SubCommands.values();
		for (SubCommands command : subs) {
			builderSubCommands.append(command).append(", ");
		}
		builderSubCommands.delete(builderSubCommands.length() - 2, builderSubCommands.length());
		listSubCommands = builderSubCommands.toString();
	}

	@Override
	public String getName() {
		return "tickdynamic";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "tickdynamic [" + listSubCommands + "]";
	}

	@Override
	public List getAliases() {
		return aliases;
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (args.length == 0) {
			sender.sendMessage(new TextComponentString("Usage: " + getUsage(sender)));
			return;
		}

		switch (args[0]) {
			case "tps":

				sender.sendMessage(new TextComponentString("Average TPS: " + getTPSFormatted() + " TPS"));
				return;
			case "identify":
				sender.sendMessage(new TextComponentString("Command not yet implemented! This will allow you to check what group a Tile or Entity belongs to by right clicking it.(And other info, like TPS)"));
				return;
			case "help":
				sender.sendMessage(new TextComponentString("You can find the documentation over at http://mods.stjerncraft.com/tickdynamic"));
				return;
		}

		//Send it over to subCommand handler
		ICommand subHandler = subCommandHandlers.get(args[0]);
		if (subHandler == null) {
			sender.sendMessage(new TextComponentString(TextFormatting.RED + "No handler for the command " + TextFormatting.ITALIC + args[0]));
			return;
		}
		subHandler.execute(server, sender, args);
	}


	@Override
	public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
		return sender.canUseCommand(1, getName());
	}

	@Override
	public List getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
		if (args.length == 1) {
			List listOut = new LinkedList();
			String lastArg = args[args.length - 1];
			SubCommands[] subCommands = SubCommands.values();
			for (SubCommands command : subCommands) {
				if (command.toString().contains(lastArg))
					listOut.add(command.toString());
			}

			return listOut;
		} else {
			//Send it over to subCommand handler
			ICommand subHandler = subCommandHandlers.get(args[0]);
			if (subHandler == null)
				return null;
			return subHandler.getTabCompletions(server, sender, args, pos);
		}
	}

	@Override
	public boolean isUsernameIndex(String[] args, int index) {
		//TODO: Pass on to subCommand
		return false;
	}

	@Override
	public int compareTo(ICommand o) {
		return 0;
	}

	public static String getTPSFormatted() {
		String tpsOut;
		String color;

		if (TickDynamicMod.instance.averageTPS >= 19)
			color = TextFormatting.GREEN.toString();
		else if (TickDynamicMod.instance.averageTPS > 10)
			color = TextFormatting.YELLOW.toString();
		else
			color = TextFormatting.RED.toString();

		DecimalFormat tpsFormat = new DecimalFormat("#.00");
		tpsOut = color + tpsFormat.format(TickDynamicMod.instance.averageTPS) + TextFormatting.RESET;
		return tpsOut;
	}

}
