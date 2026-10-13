package com.freightfox.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private boolean fallbackToInMemory = true;

    private Tier login = new Tier(5, 5, 60);
    private Tier register = new Tier(5, 5, 3600);
    private Tier assignment = new Tier(20, 20, 60);
    private Tier importTier = new Tier(5, 5, 60);
    private Tier general = new Tier(120, 120, 60);

    public static class Tier {
        private long capacity;
        private long refillTokens;
        private long durationSeconds;

        public Tier() {}

        public Tier(long capacity, long refillTokens, long durationSeconds) {
            this.capacity = capacity;
            this.refillTokens = refillTokens;
            this.durationSeconds = durationSeconds;
        }

        public long getCapacity() {
            return capacity;
        }

        public void setCapacity(long capacity) {
            this.capacity = capacity;
        }

        public long getRefillTokens() {
            return refillTokens;
        }

        public void setRefillTokens(long refillTokens) {
            this.refillTokens = refillTokens;
        }

        public long getDurationSeconds() {
            return durationSeconds;
        }

        public void setDurationSeconds(long durationSeconds) {
            this.durationSeconds = durationSeconds;
        }

        public Duration getDuration() {
            return Duration.ofSeconds(durationSeconds);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isFallbackToInMemory() {
        return fallbackToInMemory;
    }

    public void setFallbackToInMemory(boolean fallbackToInMemory) {
        this.fallbackToInMemory = fallbackToInMemory;
    }

    public Tier getLogin() {
        return login;
    }

    public void setLogin(Tier login) {
        this.login = login;
    }

    public Tier getRegister() {
        return register;
    }

    public void setRegister(Tier register) {
        this.register = register;
    }

    public Tier getAssignment() {
        return assignment;
    }

    public void setAssignment(Tier assignment) {
        this.assignment = assignment;
    }

    public Tier getImport() {
        return importTier;
    }

    public void setImport(Tier importTier) {
        this.importTier = importTier;
    }

    public Tier getGeneral() {
        return general;
    }

    public void setGeneral(Tier general) {
        this.general = general;
    }
}
