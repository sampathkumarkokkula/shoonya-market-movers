package com.example.shoonyamonitor;

import com.example.shoonyamonitor.config.ShoonyaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Shoonya Monitor.
 *
 * <p>Info-only dashboard: it authenticates with Shoonya, subscribes to a
 * configurable set of indices and stocks, keeps a short in-memory history of
 * ticks and continuously ranks the biggest movers over several time windows.
 * No orders are ever placed.</p>
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(ShoonyaProperties.class)
public class ShoonyaMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShoonyaMonitorApplication.class, args);
    }
}
