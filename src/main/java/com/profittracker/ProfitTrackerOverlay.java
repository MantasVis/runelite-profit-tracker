package com.profittracker;

import java.awt.*;
import java.text.DecimalFormat;

import javax.inject.Inject;
import javax.swing.*;

import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * The ProfitTrackerOverlay class is used to display profit values for the user
 */
public class ProfitTrackerOverlay extends Overlay {
    private long profitValue;
    private long startTimeMillis;
    private boolean inProfitTrackSession;

    private long timerStoppedAt = 0;
    private boolean paused = false;
    private boolean firstStart = true;

    private final ProfitTrackerConfig ptConfig;
    private final PanelComponent panelComponent = new PanelComponent();

    public static String formatIntegerWithCommas(long value) {
        DecimalFormat df = new DecimalFormat("###,###,###");
        return df.format(value);
    }

    @Inject
    private ProfitTrackerOverlay(ProfitTrackerConfig config)
    {
        setPosition(OverlayPosition.ABOVE_CHATBOX_RIGHT);
        profitValue = 0L;
        ptConfig = config;
        startTimeMillis = 0;
        inProfitTrackSession = true;
    }

    /**
     * Render the item value overlay.
     *
     * @param graphics the 2D graphics
     * @return the value of {@link PanelComponent#render(Graphics2D)} from this panel implementation.
     */
    @Override
    public Dimension render(Graphics2D graphics) {
        String titleText = "Profit Tracker:";
        long secondsElapsed;
        long profitRateValue;

        if (ptConfig.pauseTracker() && !paused) {
            if (firstStart) {
                firstStart = false;
            } else {
                pauseTimer();
            }
            paused = true;
            inProfitTrackSession = false;
        }

        if (!ptConfig.pauseTracker() && paused) {
            resumeTimer();
            paused = false;
            inProfitTrackSession = true;
        }

        if (!ptConfig.pauseTracker() && !paused && firstStart) {
            firstStart = false;
            inProfitTrackSession = true;
            startTimeMillis = System.currentTimeMillis();
        }

        if (paused) {
            secondsElapsed = timerStoppedAt != 0 ? timerStoppedAt / 1000 : 0;
        }
        else if (startTimeMillis > 0)
        {
            secondsElapsed = (System.currentTimeMillis() - startTimeMillis) / 1000;
        }
        else
        {
            // there was never any session
            secondsElapsed = 0;
        }

        profitRateValue = calculateProfitHourly(secondsElapsed, profitValue);

        panelComponent.getChildren().clear();

        // Build overlay title
        panelComponent.getChildren().add(TitleComponent.builder()
                .text(titleText)
                .color(Color.GREEN)
                .build());

        if (!inProfitTrackSession)
        {
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Plugin is paused")
                    .color(Color.RED)
                    .build());
        }

        // Set the size of the overlay (width)
        panelComponent.setPreferredSize(new Dimension(
                graphics.getFontMetrics().stringWidth(titleText) + 40,
                0));

        // Elapsed time
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Time:")
                .right(formatTimeIntervalFromSec(secondsElapsed))
                .build());

        // Profit
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Profit:")
                .right(formatIntegerWithCommas(profitValue))
                .build());

        // Profit Rate
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Rate:")
                .right(profitRateValue + "K/H")
                .build());

        return panelComponent.render(graphics);
    }


    public void startSession()
    {
        SwingUtilities.invokeLater(() ->
                inProfitTrackSession = true
        );

        initVariables();
    }

    private void initVariables() {
        profitValue = 0;
        startTimeMillis = System.currentTimeMillis();
        inProfitTrackSession = true;
        timerStoppedAt = 0;
        paused = false;
        firstStart = false;
    }

    /**
     * Updates profit value display
     *
     * @param newValue the value to update the profitValue's {{@link #panelComponent}} with.
     */
    public void updateProfitValue(final long newValue) {
        SwingUtilities.invokeLater(() ->
                profitValue = newValue
        );
    }

    /**
     * Updates startTimeMillis display
     */
    public void updateStartTimeMillis(final long newValue) {
        SwingUtilities.invokeLater(() ->
                startTimeMillis = newValue
        );
    }

    /**
     * Pauses the timer by calculating the time passed
     */
    public void pauseTimer() {
        timerStoppedAt = System.currentTimeMillis() - startTimeMillis;
    }

    /**
     * Resumes the timer by subtracting the already passed time from the current time
     */
    public void resumeTimer() {
        startTimeMillis = System.currentTimeMillis() - timerStoppedAt;
    }

    /**
     * Formats the milliseconds to HH:MM:SS format
     *
     * @param totalSecElapsed milliseconds to format
     * @return formatted time
     */
    private static String formatTimeIntervalFromSec(final long totalSecElapsed)
    {
        /*
        elapsed seconds to format HH:MM:SS
         */
        final long sec = totalSecElapsed % 60;
        final long min = (totalSecElapsed / 60) % 60;
        final long hr = totalSecElapsed / 3600;

        return String.format("%02d:%02d:%02d", hr, min, sec);
    }

    static long calculateProfitHourly(long secondsElapsed, long profit)
    {
        long averageProfitThousandForHour;
        long averageProfitForSecond;

        averageProfitForSecond = secondsElapsed != 0 ? (profit) / secondsElapsed : 0;

        averageProfitThousandForHour = averageProfitForSecond * 3600 / 1000;

        return averageProfitThousandForHour;
    }
}
