package com.elmakers.mine.bukkit.persistence;

import com.elmakers.mine.bukkit.persistence.annotation.PersistField;

public class FieldInfo
{
	public FieldInfo()
	{
	}
	
	public FieldInfo(String name)
	{
		this.name = name;
	}
	
	public FieldInfo(PersistField fieldInfo)
	{
		id = fieldInfo.id();
		auto = fieldInfo.auto();
		contained = fieldInfo.contained();
		readonly = fieldInfo.readonly();
		name = fieldInfo.name();
	}
	
	public boolean isIdField()
	{
		return id;
	}
	
	public void setIdField(boolean id)
	{
		this.id = id;
	}
	
	public boolean isAutogenerated()
	{
		return auto;
	}
	
	public void setAutogenerated(boolean auto)
	{
		this.auto = auto;
	}
	
	public boolean isContained()
	{
		return contained;
	}
	
	public void setContained(boolean contained)
	{
		this.contained = contained;
	}
	
	public boolean isReadOnly()
	{
		return readonly;
	}
	
	public void setReadOnly(boolean readonly)
	{
		this.readonly = readonly;
	}
	
	public String getName()
	{
		return name;
	}
	
	public void setName(String name)
	{
		this.name = name;
	}
	
	public void setGetter(String getter)
	{
		this.getter = getter;
	}

	public String getGetter()
	{
		return getter;
	}

	public String getSetter()
	{
		return setter;
	}

	public void setSetter(String setter)
	{
		this.setter = setter;
	}

	public String getField()
	{
		return field;
	}

	public void setField(String field)
	{
		this.field = field;
	}
	
	private boolean id = false;
	private boolean auto = false;
	private boolean contained = false;
	private boolean readonly = false;
	private String name;
	private String getter;
	private String setter;
	private String field;
}
