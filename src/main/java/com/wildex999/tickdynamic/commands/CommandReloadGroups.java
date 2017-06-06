package com.wildex999.tickdynamic.commands;

import com.wildex999.tickdynamic.TickDynamicMod;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import java.util.List;

public class CommandReloadGroups implements ICommand {

	private TickDynamicMod mod;

	public CommandReloadGroups(TickDynamicMod mod) {
		this.mod = mod;
	}

	@Override
	public String getName() {
		return "tickdynamic reloadgroups";
	}

	@Override
	public String getUsage(ICommandSender p_71518_1_) {
		return "tickdynamic reloadgroups";
	}

	@Override
	public List getAliases() {
		return null;
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
		sender.sendMessage(new TextComponentString("Reloading Groups configuration..."));
		sender.sendMessage(new TextComponentString("Note: Moving of (tile)entities to new groups might cause lag!!!"));
		mod.loadConfig(true);
		sender.sendMessage(new TextComponentString("Reload and moving complete!"));
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
