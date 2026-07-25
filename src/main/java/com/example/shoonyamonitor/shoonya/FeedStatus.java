package com.example.shoonyamonitor.shoonya;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/** Shared, thread-safe view of the upstream feed status for the UI footer. */
@Component
public class FeedStatus {

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private volatile String mode = "MOCK";

    public boolean isConnected() {
        return connected.get();
    }

    public void setConnected(boolean value) {
        connected.set(value);
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }
}
