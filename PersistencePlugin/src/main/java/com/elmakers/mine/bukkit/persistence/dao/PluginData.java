package com.elmakers.mine.bukkit.persistence.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;

import com.elmakers.mine.bukkit.persistence.annotation.PersistClass;
import com.elmakers.mine.bukkit.persistence.annotation.PersistField;
import com.elmakers.mine.craftbukkit.persistence.Persistence;
/**
 * A class to encapsulate data for a plugin.
 * 
 * Each plugin can register any number of messages and commands.
 * 
 * @author NathanWolf
 *
 */
@PersistClass(schema="global", name="plugin")
public class PluginData extends Persisted
{

	public PluginData()
	{
	}
	
	public PluginData(Plugin plugin)
	{
		update(plugin);
	}
	
	public void update(Plugin plugin)
	{
		PluginDescriptionFile pdfFile = plugin.getDescription();
		id = pdfFile.getName();
		version = pdfFile.getVersion();
		description = pdfFile.getDescription();
		authors = new ArrayList<String>();
		if (authors == null) 
		{
			authors = new ArrayList<String>();
		}
		authors.addAll(pdfFile.getAuthors());
		website = pdfFile.getWebsite();
	}
	
	public void initializeCache(List<Message> allMessages, List<PluginCommand> allCommands)
	{
		for (Message message : allMessages)
		{
			if (message.getPlugin().getId().equalsIgnoreCase(id))
			{
				messageMap.put(message.getMessageId(), message);
				messages.add(message);
			}
		}
		
		// Only cache root commands- the rest are in tree form
		for (PluginCommand command : allCommands)
		{
			if (command.getParent() == null && command.getPlugin().getId().equalsIgnoreCase(id))
			{
				commandMap.put(command.getCommand(), command);
				commands.add(command);
			}
		}
	}
	
	public Message getMessage(String messageId, String defaultValue)
	{
		// First, look up existing message
		Message message = messageMap.get(messageId);
		if (message != null) return message;
		
		// Create a new message
		message = new Message(this, messageId, defaultValue);
		messageMap.put(messageId, message);
		messages.add(message);
		
		Persistence persistence = Persistence.getInstance();
		persistence.put(message);
		
		return message;
	}
	
	public PluginCommand getCommand(String commandName, String defaultTooltip, String defaultUsage, CommandSenderData sender, PermissionType pType)
	{
		// First, look for a root command by this name-
		// command map only holds root commands!
		PluginCommand command = commandMap.get(commandName);
		if (command != null) 
		{
			command.setPermissionType(pType);
			List<CommandSenderData> senders = command.getSenders();
			if (sender != null)
			{
				if (senders == null)
				{
					senders = new ArrayList<CommandSenderData>();
				}
				if (!senders.contains(sender))
				{
					senders.add(sender);
					command.setSenders(senders);
				}
			}
			return command;
		}
		
		// Create a new un-parented command
		command = new PluginCommand(this, commandName, defaultTooltip, pType);
		command.addUsage(defaultUsage);
		
		if (sender != null)
		{
			command.addSender(sender);
		}

		Persistence persistence = Persistence.getInstance();
		persistence.put(command);
		commandMap.put(commandName, command);
		commands.add(command);
		
		return command;
	}
	
	@PersistField
	public String getVersion()
	{
		return version;
	}

	public void setVersion(String version)
	{
		this.version = version;
	}

	@PersistField
	public String getDescription()
	{
		return description;
	}

	public void setDescription(String description)
	{
		this.description = description;
	}

	@PersistField
	public List<String> getAuthors()
	{
		return authors;
	}

	public void setAuthors(List<String> authors)
	{
		this.authors = authors;
	}

	@PersistField
	public String getWebsite()
	{
		return website;
	}

	public void setWebsite(String website)
	{
		this.website = website;
	}

	@PersistField(id=true)
	public String getId()
	{
		return id;
	}

	public void setId(String id)
	{
		this.id = id;
	}
	
	
	// Transient accessors for cache map
	public List<PluginCommand> getCommands()
	{
		return commands;
	}

	public List<Message> getMessages()
	{
		return messages;
	}

	protected String			id;
	protected String			version;
	protected String			description;
	protected List<String>		authors;
	protected String			website;
	
	// Command / message cache- transient
	protected HashMap<String, Message> messageMap = new HashMap<String, Message>();
	protected HashMap<String, PluginCommand> commandMap = new HashMap<String, PluginCommand>();
	protected List<PluginCommand>	commands = new ArrayList<PluginCommand>();
	protected List<Message>			messages = new ArrayList<Message>();
}
