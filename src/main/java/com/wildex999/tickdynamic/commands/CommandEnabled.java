package com.wildex999.tickdynamic.commands;

import com.wildex999.tickdynamic.TickDynamicMod;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.List;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class CommandEnabled implements ICommand {

	private TickDynamicMod mod;
	private List listYes;
	private List listNo;

	public CommandEnabled(TickDynamicMod mod) {
		this.mod = mod;

		listYes = new ArrayList();
		listYes.add("yes");
		listNo = new ArrayList();
		listNo.add("no");
	}

	@Override
	public String getName() {
		return "tickdynamic enabled";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "tickdynamic enabled [yes, y, no, n]";
	}

	@Override
	public List getAliases() {
		return null;
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
		if (args.length == 1) {
			if (mod.enabled)
				sender.sendMessage(new TextComponentString("Tick Dynamic is currently " + TextFormatting.GREEN + " Enabled!"));
			else
				sender.sendMessage(new TextComponentString("Tick Dynamic is currently " + TextFormatting.RED + " Disabled!"));
			sender.sendMessage(new TextComponentString("Usage: " + getUsage(sender)));
			return;
		}

		if (args[1].equals("yes") || args[1].equals("y")) {
			if (mod.enabled) {
				sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "Tick Dynamic is already enabled!"));
				return;
			}
			mod.enabled = true;
			sender.sendMessage(new TextComponentString("Tick Dynamic is now " + TextFormatting.GREEN + "Enabled!"));
			return;
		} else if (args[1].equals("no") || args[1].equals("n")) {
			if (!mod.enabled) {
				sender.sendMessage(new TextComponentString(TextFormatting.RED + "Tick Dynamic is already disabled!"));
				return;
			}
			mod.enabled = false;
			sender.sendMessage(new TextComponentString("Tick Dynamic is now " + TextFormatting.RED + "Disabled!"));
			return;
		}

		sender.sendMessage(new TextComponentString("Unrecognized argument: " + args[1]));
	}

	@Override
	public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
		return sender.canUseCommand(1, getName());
	}

	@Override
	public List getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
		if (args[args.length - 1].startsWith("y"))
			return listYes;
		else if (args[args.length - 1].startsWith("n"))
			return listNo;
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
