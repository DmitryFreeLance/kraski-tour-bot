package ru.kraskitour.bot.max;

import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.kraskitour.bot.KraskiTourBot;

public class MaxLongPoller {
    private static final Logger log = LoggerFactory.getLogger(MaxLongPoller.class);

    private final MaxApiClient api;
    private final KraskiTourBot bot;
    private final int timeoutSec;
    private final int limit;
    private final long errorBackoffMs;

    private Long marker;

    public MaxLongPoller(MaxApiClient api, KraskiTourBot bot, int timeoutSec, int limit, long errorBackoffMs) {
        this.api = api;
        this.bot = bot;
        this.timeoutSec = timeoutSec;
        this.limit = limit;
        this.errorBackoffMs = errorBackoffMs;
    }

    public void runForever() {
        while (true) {
            try {
                MaxApiClient.UpdatesPage page = api.getUpdates(marker, timeoutSec, limit);
                if (page.marker != null) {
                    marker = page.marker;
                }
                ArrayNode updates = page.updates;
                if (updates != null) {
                    updates.forEach(bot::handleUpdate);
                }
            } catch (Exception e) {
                log.warn("Long polling error", e);
                sleep(errorBackoffMs);
            }
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
