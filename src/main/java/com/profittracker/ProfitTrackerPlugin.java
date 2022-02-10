package com.profittracker;

import javax.inject.Inject;

import com.google.inject.Provides;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.ObjectID;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
        name = "Profit Tracker"
)
public class ProfitTrackerPlugin extends Plugin
{
    ProfitTrackerGoldDrops goldDropsObject;
    ProfitTrackerInventoryValue inventoryValueObject;

    // the profit will be calculated against this value
    private long prevInventoryValue;
    private long totalProfit;

    private long startTickMillis;

    private boolean skipTickForProfitCalculation;
    private boolean inventoryValueChanged;
    private boolean inProfitTrackSession;

    private boolean firstStart = true;
    private boolean paused = false;

    @Inject
    private Client client;

    @Inject
    private ProfitTrackerConfig config;

    @Inject
    private ItemManager itemManager;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ProfitTrackerOverlay overlay;

    @Override
    protected void startUp() throws Exception
    {
        // Add the inventory overlay
        overlayManager.add(overlay);

        goldDropsObject = new ProfitTrackerGoldDrops(client, itemManager);

        inventoryValueObject = new ProfitTrackerInventoryValue(client, itemManager, config);

        initializeVariables();

        if (client.getGameState() == GameState.LOGGED_IN)
        {
            startProfitTrackingSession();
        }
    }

    private void initializeVariables()
    {
        // value here doesn't matter, will be overwritten
        prevInventoryValue = -1;

        // profit begins at 0 of course
        totalProfit = 0;

        // this will be filled with actual information in startProfitTrackingSession
        startTickMillis = 0;

        // skip profit calculation for first tick, to initialize first inventory value
        skipTickForProfitCalculation = true;

        inventoryValueChanged = false;

        inProfitTrackSession = true;
    }

    /**
     * Starts tracking profit
     */
    private void startProfitTrackingSession()
    {
        initializeVariables();

        // initialize timer
        startTickMillis = System.currentTimeMillis();

        overlay.updateStartTimeMillis(startTickMillis);
        overlay.startSession();

        inProfitTrackSession = true;
    }

    @Override
    protected void shutDown() {
        // Remove the inventory overlay
        overlayManager.remove(overlay);
    }

    /**
     * Main plugin logic
     *
     * 1. If inventory changed,
     *      -  calculate profit (inventory value difference)
     *      - generate gold drop (nice animation for showing gold earn or loss)
     *
     * 2. Calculate profit rate and update in overlay
     *
     * @param gameTick
     */
    @Subscribe
    public void onGameTick(GameTick gameTick)
    {
        if (firstStart && client.getGameState() == GameState.LOGGED_IN) {
            startProfitTrackingSession();
            firstStart = false;
        }

        long tickProfit;

        if (config.pauseTracker() && !paused) {
            inProfitTrackSession = false;
            paused = true;
        }

        if (!config.pauseTracker() && paused) {
            inProfitTrackSession = true;
            paused = false;
        }

        if (!inProfitTrackSession)
        {
            return;
        }

        if (inventoryValueChanged)
        {
            tickProfit = calculateTickProfit();

            // accumulate profit
            totalProfit += tickProfit;

            overlay.updateProfitValue(totalProfit);

            // generate gold drop
            if (config.goldDrops() && tickProfit != 0)
            {
                goldDropsObject.requestGoldDrop(tickProfit);
            }

            inventoryValueChanged = false;
        }
    }

    /**
     * Calculate and return the profit for this tick
     * If skipTickForProfitCalculation is set, meaning this tick was bank/deposit so return 0
     *
     * @return calculated profit
     */
    private long calculateTickProfit()
    {
        long newInventoryValue;
        long newProfit;

        // calculate current inventory value
        newInventoryValue = inventoryValueObject.calculateInventoryAndEquipmentValue();

        if (!skipTickForProfitCalculation)
        {
            // calculate new profit
            newProfit = newInventoryValue - prevInventoryValue;

        } else {
            // first time calculation / banking / equipping
            log.info("Skipping profit calculation!");

            skipTickForProfitCalculation = false;

            // no profit this tick
            newProfit = 0;
        }

        // update prevInventoryValue for future calculations anyway!
        prevInventoryValue = newInventoryValue;

        return newProfit;
    }

    /**
     * This event tells us when inventory has changed and when banking/equipment event occurred this tick
     *
     * @param event
     */
    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        log.info("onItemContainerChanged container id: " + event.getContainerId());

        int containerId = event.getContainerId();

        if (containerId == InventoryID.INVENTORY.getId() ||
                containerId == InventoryID.EQUIPMENT.getId()) {
            // inventory has changed - need calculate profit in onGameTick
            inventoryValueChanged = true;
        }

        // in these events, inventory WILL be changed, but we DON'T want to calculate profit!
        if(containerId == InventoryID.BANK.getId()) {
            // this is a bank interaction.
            // Don't take this into account
            skipTickForProfitCalculation = true;
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        /* for ignoring deposit in deposit box */
        log.info(String.format("Click! ID: %d, actionParam: %d ,menuOption: %s, menuTarget: %s, widgetId: %d",
                event.getId(), event.getActionParam(), event.getMenuOption(), event.getMenuTarget(), event.getWidgetId()));

        if (event.getId() == ObjectID.BANK_DEPOSIT_BOX) {
            // we've interacted with a deposit box. Don't take this tick into account for profit calculation
            skipTickForProfitCalculation = true;
        }
    }

    @Provides
    ProfitTrackerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ProfitTrackerConfig.class);
    }


    @Subscribe
    public void onScriptPreFired(ScriptPreFired scriptPreFired) {
        goldDropsObject.onScriptPreFired(scriptPreFired);
    }
}
