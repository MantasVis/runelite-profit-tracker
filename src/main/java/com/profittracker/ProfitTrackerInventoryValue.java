package com.profittracker;

import static com.profittracker.ProfitTrackerConstants.CANNON_BARRELS;
import static com.profittracker.ProfitTrackerConstants.CANNON_BASE;
import static com.profittracker.ProfitTrackerConstants.CANNON_FURNACE;
import static com.profittracker.ProfitTrackerConstants.CANNON_STAND;
import static com.profittracker.ProfitTrackerConstants.EMPTY_SLOT_ITEMID;

import java.util.Arrays;
import java.util.stream.LongStream;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.client.game.ItemManager;

/**
 * Provides functional methods for calculating inventory value
 */
@Slf4j
public class ProfitTrackerInventoryValue {

    private final ItemManager itemManager;
    private final Client client;
    private final ProfitTrackerConfig config;

    public ProfitTrackerInventoryValue(Client client, ItemManager itemManager, ProfitTrackerConfig config) {
        this.client = client;
        this.itemManager = itemManager;
        this.config = config;
    }

    /**
     * Calculates GE value of single item
     *
     * @param item value to calculate
     * @return value of the specified item
     */
    private long calculateItemValue(Item item) {

        if (item.getId() < -1) {
            log.info("Bad item id!" + item.getId());
            return 0;
        }

        if (item.getId() == EMPTY_SLOT_ITEMID) {
            return 0;
        }

        if (config.ignoreDwarfCannon() && Arrays.asList(CANNON_BASE, CANNON_STAND, CANNON_BARRELS, CANNON_FURNACE).contains(item.getId())) {
            return 0;
        }

        log.info(String.format("calculateItemValue itemId = %d", item.getId()));

        // multiply quantity  by GE value
        return (long) item.getQuantity() * (itemManager.getItemPrice(item.getId()));
    }

    /**
     * Calculates specified container value
     *
     * @param ContainerID container to calculate
     * @return the value of the container
     */
    public long calculateContainerValue(InventoryID ContainerID) {
        long newInventoryValue;

        ItemContainer container = client.getItemContainer(ContainerID);

        if (container == null) {
            return 0;
        }

        Item[] items = container.getItems();

        newInventoryValue = Arrays.stream(items).flatMapToLong(item -> LongStream.of(calculateItemValue(item))).sum();

        return newInventoryValue;
    }

    /**
     * Calculate total inventory and equipment value
     *
     * @return the value of inventory and equipment
     */
    public long calculateInventoryAndEquipmentValue() {
        return calculateContainerValue(InventoryID.INVENTORY) + calculateContainerValue(InventoryID.EQUIPMENT);
    }
}
