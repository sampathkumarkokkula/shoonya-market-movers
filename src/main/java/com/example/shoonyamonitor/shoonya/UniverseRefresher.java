package com.example.shoonyamonitor.shoonya;

import com.example.shoonyamonitor.config.ShoonyaProperties;
import com.example.shoonyamonitor.store.InstrumentRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Refreshes the instrument universe once per trading day so newly listed scrips
 * are picked up and delisted ones drop off.
 *
 * <p>Runs only when {@code shoonya.universe-enabled=true}. It reloads the
 * {@link InstrumentRegistry} from the symbol master and, if the live feed is
 * running, restarts it so the updated universe is re-subscribed. The feed
 * client bean exists only in live mode, so it is resolved lazily via an
 * {@link ObjectProvider} to keep this component usable in mock mode too.</p>
 */
@Component
public class UniverseRefresher {

    private static final Logger log = LoggerFactory.getLogger(UniverseRefresher.class);

    private final ShoonyaProperties props;
    private final InstrumentRegistry registry;
    private final ObjectProvider<ShoonyaFeedClient> feedClient;

    public UniverseRefresher(ShoonyaProperties props,
                             InstrumentRegistry registry,
                             ObjectProvider<ShoonyaFeedClient> feedClient) {
        this.props = props;
        this.registry = registry;
        this.feedClient = feedClient;
    }

    /** Runs on trading days shortly before the market opens (IST). */
    @Scheduled(cron = "${shoonya.universe-refresh-cron:0 0 8 * * MON-FRI}", zone = "Asia/Kolkata")
    public void refresh() {
        if (!props.isUniverseEnabled()) {
            return;
        }
        try {
            int total = registry.reload();
            log.info("Universe refreshed: {} instruments now tracked.", total);
            ShoonyaFeedClient client = feedClient.getIfAvailable();
            if (client != null) {
                client.restart();
            }
        } catch (Exception e) {
            log.error("Universe refresh failed ({}); keeping the current universe.",
                    e.getMessage());
        }
    }
}
