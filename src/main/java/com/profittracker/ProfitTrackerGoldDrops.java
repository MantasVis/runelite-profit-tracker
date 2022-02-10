package com.profittracker;

import static net.runelite.api.ScriptID.XPDROPS_SETDROPSIZE;
import static net.runelite.api.ScriptID.XPDROP_DISABLED;
import static net.runelite.api.widgets.WidgetInfo.TO_CHILD;
import static net.runelite.api.widgets.WidgetInfo.TO_GROUP;

import java.awt.*;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.api.SpritePixels;
import net.runelite.api.Varbits;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

/**
 * Implement gold drops.
 * We do this by using the XPDrop mechanism, namely the Fake XPDrop script, which is intended to generate xp drops for maxed out skills.
 * Fake XP Drops are composed of a skill sprite, and a text widget with a mod icon (<img=11> in text).
 * So to create a gold drop, we create a fake xp drop, and interfere in the middle, and change the sprite and text to our liking.
 *
 * Flow is:
 * 1. create xp drop using runScript (see requestGoldDrop)
 * 2. getting in the middle of the drop, changing icon and text (see handleXpDrop)
 *
 * A more correct way to do this is probably by calling Item.GetImage with wanted coin quantity, which will give us correct coin icon and correct text,
 * and simply drawing that image ourself somehow, instead of using xp drop mechanism.
 */
@Slf4j
public class ProfitTrackerGoldDrops {

    private final ItemManager itemManager;
    private final Client client;

    //Free sprite id for the gold icons.
    private static final int COINS_SPRITE_ID = -1337;

    /* Skill ordinal to send in the fake xp drop script.
    doesn't matter which skill except it's better not be attack/defense/magic to avoid collision with
    XpDropPlugin which looks for those and might change text color. */
    private static final int XPDROP_SKILL = Skill.FISHING.ordinal();

    /* Value to send in the fake xp drop script. Doesn't matter at all
    since we don't use this value, but we use currentGoldDropValue */
    private static final int XPDROP_VALUE = 6;

    /* var currentGoldDropValue will have the gold value of the current ongoing gold drop. 2 purposes:
      1. to know the value later when we actually use it,
      2. to know to catch the next fake xpdrop in onScriptPreFired */
    private long currentGoldDropValue;

    ProfitTrackerGoldDrops(Client client, ItemManager itemManager)
    {
        this.client = client;
        this.itemManager = itemManager;

        prepareCoinSprite();

        currentGoldDropValue = 0L;
    }

    /**
     * Prepare coin sprites for use in the gold drops.
     * It seems item icons are not available as sprites with id, so we convert in this function.
     */
    private void prepareCoinSprite()
    {
        AsyncBufferedImage coinImageRaw;

        // get image object by coin item id
        coinImageRaw = itemManager.getImage(ItemID.COINS_995, 10000, false);

        /* since getImage returns an AsyncBufferedImage, which is not loaded initially,
         we schedule sprite conversion and sprite override for when the image is actually loaded */
        coinImageRaw.onLoaded(() -> {
            final SpritePixels coinSprite;

            // convert image to sprite
            coinSprite = ImageUtil.getImageSpritePixels(coinImageRaw, client);

            // register new coin sprite by overriding a free sprite id
            client.getSpriteOverrides().put(COINS_SPRITE_ID, coinSprite);
        });
    }

    /**
     * We check for scripts of type XPDROPS_SETDROPSIZE to interfere with the XPdrop and write our own values
     *
     * @param scriptPreFired
     */
    public void onScriptPreFired(ScriptPreFired scriptPreFired)
    {

        if (scriptPreFired.getScriptId() != XPDROPS_SETDROPSIZE)
        {
            return;
        }

        // Get xpdrop widget id using the stack
        // taken from XpDropPlugin!

        // This runs prior to the proc being invoked, so the arguments are still on the stack.
        // Grab the first argument to the script.
        final int[] intStack = client.getIntStack();
        final int intStackSize = client.getIntStackSize();

        final int widgetId = intStack[intStackSize - 4];

        // extract information from currentGoldDropValue
        boolean isThisGoldDrop = (currentGoldDropValue != 0);
        long goldDropValue = currentGoldDropValue;

        // done with this gold drop anyway
        currentGoldDropValue = 0;

        handleXpDrop(widgetId, isThisGoldDrop, goldDropValue);
    }

    private void handleXpDrop(int xpDropWidgetId, boolean isThisGoldDrop, long goldDropValue)
    {
        final Widget xpDropWidget;
        final Widget dropTextWidget;

        Widget[] xpDropWidgetChildren;

        // get widget from ID
        xpDropWidget = client.getWidget(TO_GROUP(xpDropWidgetId), TO_CHILD(xpDropWidgetId));

        if (xpDropWidget == null)
        {
            log.error("xpDropWidget was null");
            return;
        }

        xpDropWidgetChildren = xpDropWidget.getChildren();

        if (xpDropWidgetChildren.length < 1)
        {
            log.error(String.format("Unexpected xpDropWidgets length! %d", xpDropWidgetChildren.length));
            return;
        }

        dropTextWidget = xpDropWidgetChildren[0];

        if (isThisGoldDrop)
        {
            final Widget dropSpriteWidget;

            if (xpDropWidgetChildren.length < 2)
            {
                log.error(String.format(
                        "Unexpected xpDropWidgetChildren length for a gold drop! length! %d",
                        xpDropWidgetChildren.length));
                return;
            }

            dropSpriteWidget = xpDropWidgetChildren[1];

            xpDropToGoldDrop(dropTextWidget, dropSpriteWidget, goldDropValue);
        }
        else
        {
            // reset text color for all regular xpdrops
            resetXpDropTextColor(dropTextWidget);
        }

    }

    /**
     * Change xpdrop icon and text, to make a gold drop
     *
     * @param dropTextWidget
     * @param dropSpriteWidget
     * @param goldDropValue
     */
    private void xpDropToGoldDrop(Widget dropTextWidget, Widget dropSpriteWidget, long goldDropValue)
    {
        dropTextWidget.setText(formatGoldDropText(goldDropValue));

        if (goldDropValue > 0)
        {
            // green text for profit
            dropTextWidget.setTextColor(Color.GREEN.getRGB());
        }
        else
        {
            // red for loss
            dropTextWidget.setTextColor(Color.RED.getRGB());
        }

        // change skill sprite to coin sprite
        dropSpriteWidget.setSpriteId(COINS_SPRITE_ID);
    }

    public void requestGoldDrop(long amount)
    {
        log.info(String.format("goldDrop: %d", amount));

        // save the value and mark an ongoing gold drop
        currentGoldDropValue = amount;

        /* Create a fake xp drop. the 2 last arguments don't matter:
        1. skill ordinal - we will replace the icon anyway
        2. value - since we want to be able to pass negative numbers, we pass the value using
        currentGoldDropValue instead of this argument */

        client.runScript(XPDROP_DISABLED, XPDROP_SKILL, XPDROP_VALUE);
    }

    private void resetXpDropTextColor(Widget xpDropTextWidget)
    {
        // taken from XpDropPlugin
        EnumComposition colorEnum = client.getEnum(EnumID.XPDROP_COLORS);
        int defaultColorId = client.getVar(Varbits.EXPERIENCE_DROP_COLOR);
        int color = colorEnum.getIntValue(defaultColorId);
        xpDropTextWidget.setTextColor(color);
    }

    /**
     * Format gold value to RuneScape style up to 10000k
     * I.E. 100,000 -> 100K
     *
     * @param goldDropValue gold value to format
     * @return formatted value
     */
    private String formatGoldDropText(long goldDropValue)
    {
        if (Math.abs(goldDropValue) < 10000L)
        {
            return Long.toString(goldDropValue);
        }
        else if (Math.abs(goldDropValue) < 1000L * 1000L)
        {
            return (goldDropValue / 1000) + "K";
        }
        else
        {
            return "A LOT";
        }
    }
}
