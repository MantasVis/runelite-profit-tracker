package com.profittracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

/**
 * The ProfitTrackerConfig class is used to provide user preferences to the ProfitTrackerPlugin.
 */
@ConfigGroup("ptconfig")
public interface ProfitTrackerConfig extends Config
{

    @ConfigItem(
            position = 1,
            keyName = "pauseTracker",
            name = "Pause tracker",
            description = "Pauses the tracker when enabled"
    )
    default boolean pauseTracker()
    {
        return false;
    }

    @ConfigItem(
            position = 2,
            keyName = "ignoreDwarfCannon",
            name = "Ignore Dwarf Multicannon",
            description = "Do not count dwarf multicannon in profits when dropping/picking it up"
    )
    default boolean ignoreDwarfCannon()
    {
        return true;
    }

    @ConfigItem(
            position = 3,
            keyName = "goldDrops",
            name = "Show value changes (gold drops) ",
            description = "Show each profit increase or decrease"
    )
    default boolean goldDrops()
    {
        return true;
    }
}

