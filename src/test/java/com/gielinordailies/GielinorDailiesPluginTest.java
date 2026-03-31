package com.gielinordailies;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class GielinorDailiesPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(GielinorDailiesPlugin.class);
		RuneLite.main(args);
	}
}
