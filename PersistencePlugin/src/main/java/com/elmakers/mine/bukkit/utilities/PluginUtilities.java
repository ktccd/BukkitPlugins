package com.elmakers.mine.bukkit.utilities;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import com.elmakers.mine.bukkit.permission.PermissionManager;
import com.elmakers.mine.bukkit.persistence.dao.CommandSenderData;
import com.elmakers.mine.bukkit.persistence.dao.Message;
import com.elmakers.mine.bukkit.persistence.dao.PermissionType;
import com.elmakers.mine.bukkit.persistence.dao.PlayerData;
import com.elmakers.mine.bukkit.persistence.dao.PluginCommand;
import com.elmakers.mine.bukkit.persistence.dao.PluginData;
import com.elmakers.mine.bukkit.persistence.dao.WorldData;
import com.elmakers.mine.craftbukkit.permission.PermissionDescriptionException;
import com.elmakers.mine.craftbukkit.permission.PermissionDescriptionNodeException;
import com.elmakers.mine.craftbukkit.permission.RootPermissionDescription;
import com.elmakers.mine.craftbukkit.persistence.Persistence;

/** 
 * An interface for displaying data-driven messages and processing data-driven commands.
 * 
 * @author NathanWolf
 *
 */
public class PluginUtilities
{
	/**
	 * Messaging constructor. Use to create an instance of Messaging for your plugin.
	 * 
	 * This can also be done via persistence.getMessaging(plugin)
	 * 
	 * @param requestingPlugin The plugin requesting the messaging interface
	 * @param persistence The Persistence reference to use for retrieving data
	 * @param permissions The permissions manager
	 */
	public PluginUtilities(Plugin requestingPlugin, Persistence persistence)
	{
		this.persistence = persistence;
		this.owner = requestingPlugin;
		
		// Retrieve or create the plugin data record for this plugin.
		PluginDescriptionFile pdfFile = requestingPlugin.getDescription();
		String pluginId = pdfFile.getName();
		plugin = persistence.get(pluginId, PluginData.class);
		if (plugin == null)
		{
			plugin = new PluginData(requestingPlugin);
			persistence.put(plugin);
		}
		
		// Let the plugin bind its transient command and message instances
		if (plugin.getCommands().isEmpty() && plugin.getMessages().isEmpty())
		{
			List<Message> allMessages = new ArrayList<Message>();
			List<PluginCommand> allCommands = new ArrayList<PluginCommand>();
			persistence.getAll(allMessages, Message.class);
			persistence.getAll(allCommands, PluginCommand.class);
			plugin.initializeCache(allMessages, allCommands);
		}
		
		playerSender = persistence.get("player", CommandSenderData.class);
	}
	
	public void loadPermissions(PermissionManager permissions)
	{
		File dataFolder = owner.getDataFolder();
		String pluginName = dataFolder.getName();
		File file = new File(dataFolder.getParentFile(), pluginName + ".jar");
		try
		{
			if (!file.exists())
			{
				throw new InvalidPluginException(new FileNotFoundException(String.format("%s does not exist",
						file.getPath())));
			}
			try
			{
				JarFile jar = new JarFile(file);
				JarEntry entry = jar.getJarEntry("plugin.yml");

				if (entry == null)
				{
					throw new InvalidPluginException(new FileNotFoundException("Jar does not contain plugin.yml"));
				}

				InputStream stream = jar.getInputStream(entry);
				
				@SuppressWarnings("unchecked")
				Map<String, Object> map = (Map<String, Object>)yaml.load(stream);
				if (map.containsKey("permissions"))
				{
					try
					{
						@SuppressWarnings("unchecked")
						Map<String, Object> perms = (Map<String, Object>)map.get("permissions");
						
						RootPermissionDescription rootNode = new RootPermissionDescription(perms);
						permissions.addPluginRootPermission(pluginName, rootNode);
					}
					catch (ClassCastException ex)
					{
						throw new InvalidDescriptionException(ex, "permissions are of wrong type");
					}
					catch (PermissionDescriptionException ex)
					{
						throw new InvalidDescriptionException(ex, "permissions are invalid");
					}
					catch (PermissionDescriptionNodeException ex)
					{
						throw new InvalidDescriptionException(ex, "permissions are invalid");
					}
				}

				stream.close();
				jar.close();
			}
			catch (IOException ex)
			{
				throw new InvalidPluginException(ex);
			}
		}
		catch (Throwable ex)
		{
			log.log(Level.INFO, "Error reading plugin permissions: ", ex);
		}
	}
	
	public Plugin getOwningPlugin()
	{
		return owner;
	}
	
	public PlayerData getPlayer(Player player)
	{
		PlayerData playerData = persistence.get(player.getName(), PlayerData.class);
		if (playerData == null)
		{
			playerData = new PlayerData(player);
		}
		else
		{
			playerData.update(player);
		}
		persistence.put(playerData);
		
		return playerData;
	}
	
	public WorldData getWorld(Server server, String name, Environment defaultType)
	{
		WorldData data = persistence.get(name, WorldData.class);
		if (data == null)
		{
			data = new WorldData(name, defaultType);
			persistence.put(data);
		}
		
		return data;
	}
	
	public WorldData getWorld(Server server, World world)
	{
		WorldData data = persistence.get(world.getName(), WorldData.class);
		if (data == null)
		{
			data = new WorldData();
			data.update(world);
			persistence.put(data);
		}
		else
		{
			data.update(world);
		}
		
		return data;
	}
	
	/**
	 * Get a message based on id, or create one using a default.
	 * 
	 * @param id The message id
	 * @param defaultString The default string to use if no value exists
	 * @return The stored message, or defaultString if none exists
	 */
	public Message getMessage(String id, String defaultString)
	{
		return plugin.getMessage(id, defaultString);
	}
	
	/**
	 * Retrieve a player command description based on id. 
	 * 
	 * A command description can be used to easily process commands, including
	 * commands with sub-commands.
	 * 
	 * This method automatically creates a player-specific (in-game) command.
	 * 
	 * @param commandName The command id to retrieve or create
	 * @param defaultTooltip The default tooltip to use if this is a new command
	 * @param defaultUsage The default usage string, more can be added
	 * @return A command descriptor
	 */
	public PluginCommand getPlayerCommand(String commandName, String defaultTooltip, String defaultUsage)
	{
		return getPlayerCommand(commandName, defaultTooltip, defaultUsage, PermissionType.DEFAULT);
	}
	
	/**
	 * Retrieve a player command description based on id. 
	 * 
	 * A command description can be used to easily process commands, including
	 * commands with sub-commands.
	 * 
	 * This method automatically creates a player-specific (in-game) command.
	 * 
	 * @param commandName The command id to retrieve or create
	 * @param defaultTooltip The default tooltip to use if this is a new command
	 * @param defaultUsage The default usage string, more can be added
	 * @param pType The type of permissions to apply to this command
	 * @return A command descriptor
	 */
	public PluginCommand getPlayerCommand(String commandName, String defaultTooltip, String defaultUsage, PermissionType pType)
	{
		return getCommand(commandName, defaultTooltip, defaultUsage, playerSender, pType);
	}
	
	/**
	 * Retrieve a general command description based on id. 
	 * 
	 * A command description can be used to easily process commands, including
	 * commands with sub-commands.
	 * 
	 * This method automatically creates a general command that will be passed
	 * a CommandSender for use as a server or in-game command.
	 * 
	 * @param commandName The command id to retrieve or create
	 * @param defaultTooltip The default tooltip to use if this is a new command
	 * @param defaultUsage The default usage string, more can be added
	 * @return A command descriptor
	 */
	public PluginCommand getGeneralCommand(String commandName, String defaultTooltip, String defaultUsage)
	{
		return getGeneralCommand(commandName, defaultTooltip, defaultUsage, PermissionType.DEFAULT);
	}
	
	/**
	 * Retrieve a general command description based on id. 
	 * 
	 * A command description can be used to easily process commands, including
	 * commands with sub-commands.
	 * 
	 * This method automatically creates a general command that will be passed
	 * a CommandSender for use as a server or in-game command.
	 * 
	 * @param commandName The command id to retrieve or create
	 * @param defaultTooltip The default tooltip to use if this is a new command
	 * @param defaultUsage The default usage string, more can be added
	 * @param pNode Override the default permission node
	 * @param pType The type of permissions to apply to this command
	 * @return A command descriptor
	 */
	public PluginCommand getGeneralCommand(String commandName, String defaultTooltip, String defaultUsage, PermissionType pType)
	{
		return getCommand(commandName, defaultTooltip, defaultUsage, null, pType);
	}
	
	/**
	 * Retrieve a command description based on id, for a given sender
	 * 
	 * A command description can be used to easily process commands, including
	 * commands with sub-commands.
	 * 
	 * @param commandName The command id to retrieve or create
	 * @param defaultTooltip The default tooltip to use if this is a new command
	 * @param defaultUsage The default usage string, more can be added
	 * @param sender The sender that will issue this command
	 * @return A command descriptor
	 */
	public PluginCommand getCommand(String commandName, String defaultTooltip, String defaultUsage, CommandSenderData sender, PermissionType pType)
	{
		return plugin.getCommand(commandName, defaultTooltip, defaultUsage, sender, pType);
	}


	/**
	 * Dispatch any automatically bound command handlers.
	 * 
	 * Any commands registered with this plugin that around bound() to a command handler will be automatically called.
	 * 
	 * For Player commands, the signature should be:
	 * 
	 * public boolean onMyCommand(Player player, String[] parameters)
	 * {
	 * }
	 * 
	 * For General commands, a CommandSender should be used in place of Player.
	 * 
	 * @param listener The class that will handle the command callback
	 * @param sender The sender of this command
	 * @param baseCommand The base command issues
	 * @param baseParameters Any parameters (or sub-commands) passed to the base command 
	 * @see PluginCommand#bind(String)
	 */
	public boolean dispatch(Object listener, CommandSender sender, String baseCommand, String[] baseParameters)
	{
		List<Object> listeners = new ArrayList<Object>();
		listeners.add(listener);
		return dispatch(listeners, sender, baseCommand, baseParameters);
	}
	
	/**
	 * Dispatch any automatically bound command handlers.
	 * 
	 * Any commands registered with this plugin that around bound() to a command handler will be automatically called.
	 * 
	 * For Player commands, the signature should be:
	 * 
	 * public boolean onMyCommand(Player player, String[] parameters)
	 * {
	 * }
	 * 
	 * For General commands, a CommandSender should be used in place of Player.
	 * 
	 * @param listeners The class that will handle the command callback
	 * @param sender The sender of this command
	 * @param baseCommand The base command issues
	 * @param baseParameters Any parameters (or sub-commands) passed to the base command 
	 * @see PluginCommand#bind(String)
	 */
	public boolean dispatch(List<Object> listeners, CommandSender sender, String baseCommand, String[] baseParameters)
	{
		List<PluginCommand> baseCommands = plugin.getCommands();
		if (baseCommands == null) return false;
		
		List<PluginCommand> commandsCopy = new ArrayList<PluginCommand>();
		commandsCopy.addAll(baseCommands);
		
		for (PluginCommand command : commandsCopy)
		{
			boolean success = dispatch(listeners, sender, command, baseCommand, baseParameters);
			if (success) return true;
		}
		return false;
	}
	
	protected boolean dispatch(List<Object> listeners, CommandSender sender, PluginCommand command, String commandString, String[] parameters)
	{		
		if (command != null && command.checkCommand(sender, commandString))
		{			
			boolean handledByChild = false;
			if (parameters != null && parameters.length > 0)
			{
				String[] childParameters = new String[parameters.length - 1];
				for (int i = 0; i < childParameters.length; i++)
				{
					childParameters[i] = parameters[i + 1];
				}
				String childCommand = parameters[0];
				
				List<PluginCommand> subCommands = command.getChildren();
				if (subCommands != null)
				{
					List<PluginCommand> commandsCopy = new ArrayList<PluginCommand>();
					commandsCopy.addAll(subCommands);
					
					for (PluginCommand child : commandsCopy)
					{
						handledByChild = dispatch(listeners, sender, child, childCommand, childParameters);
						if (handledByChild)
						{
							return true;
						}
					}
				}
			}
			
			// Not handled by a sub-child, so handle it ourselves.
			
			if (!command.checkPermission(sender))
			{
				return true;
			}

			String callbackName = command.getCallbackMethod();
			if (callbackName == null || callbackName.length() <= 0) 
			{
				// auto help for commands that only have sub-commands
				command.sendUse(sender);
				return true;
			}
			
			for (Object listener : listeners)
			{
				try
				{
					List<CommandSenderData> senders = command.getSenders();
					
					if (senders != null)
					{
						for (CommandSenderData senderData : senders)
						{
							Class<?> senderType = senderData.getType();
							if (senderType == null) continue;
							if (!senderType.isAssignableFrom(sender.getClass())) continue;
							try
							{
								Method customHandler;
								customHandler = listener.getClass().getMethod(callbackName, senderType, String[].class);
								try
								{
									return (Boolean)customHandler.invoke(listener, senderType.cast(sender), parameters);
								}
								catch(InvocationTargetException clientEx)
								{
									log.severe("Error invoking callback '" + callbackName);
									clientEx.getTargetException().printStackTrace();
									return false;
								}
								catch(Throwable clientEx)
								{
									log.severe("Error invoking trying to invoke callback '" + callbackName);
									clientEx.printStackTrace();
									return false;
								}
							}
							catch (NoSuchMethodException e)
							{
							}					
						}
		
						try
						{
							Method genericHandler;
							genericHandler = listener.getClass().getMethod(callbackName, CommandSender.class, String[].class);
							return (Boolean)genericHandler.invoke(listener, sender, parameters);
						}
						catch (NoSuchMethodException ex)
						{
						}	
					}
				}
				catch (SecurityException ex)
				{
					log.warning("Persistence: Can't access callback method " + callbackName + " of " + listener.getClass().getName() + ", make sure it's public");
				}
				catch (IllegalArgumentException ex)
				{
					log.warning("Persistence: Can't find callback method " + callbackName + " of " + listener.getClass().getName() + " with the correct signature, please consult the docs.");
				}
				catch (IllegalAccessException ex)
				{
					log.warning("Persistence: Can't access callback method " + callbackName + " of " + listener.getClass().getName());
				}
				catch (InvocationTargetException ex)
				{
					log.severe("Persistence: Error invoking callback method " + callbackName + " of " + listener.getClass().getName());
					ex.printStackTrace();
				}
			}
			
			log.info("Peristence: Can't find callback '" + callbackName + " for plugin " + plugin.getId());
		}
		
		return false;
	}
	
	// TODO : These should go in Server!
	public Persistence getPersistence()
	{
		if (persistence == null)
		{
			persistence = Persistence.getInstance();
		}
		return persistence;
	}

	private Persistence			persistence;
	private Plugin				owner;
	private PluginData			plugin;
	private CommandSenderData	playerSender;
	private static final Logger	log		= Persistence.getLogger();
	private static final Yaml	yaml	= new Yaml(new SafeConstructor());
}
