package com.example.randomnumberapi.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
public class RandomNumberService {

    private final LatestNumberFileStore latestNumberFileStore;

    public RandomNumberService(LatestNumberFileStore latestNumberFileStore) {
        this.latestNumberFileStore = latestNumberFileStore;
    }

    public int generateNew(String label, int min, int max) {
        validateLabel(label);
        validateBounds(min, max);
        int generated = (int) ThreadLocalRandom.current().nextLong(min, (long) max + 1L);
        log.debug("Generated number={} for label='{}' in range [{}, {}]", generated, label, min, max);
        latestNumberFileStore.write(label, generated);
        return generated;
    }

    public int getStored(String label) {
        validateLabel(label);
        log.debug("Reading stored value for label='{}'", label);
        return latestNumberFileStore.read(label)
            .orElseThrow(() -> {
                log.warn("No stored value found for label='{}'", label);
                return new IllegalStateException("No stored value found for label: " + label);
            });
    }

    private void validateBounds(int min, int max) {
        if (min > max) {
            log.warn("Invalid bounds: min={} > max={}", min, max);
            throw new IllegalArgumentException("min must be less than or equal to max");
        }
    }

    private void validateLabel(String label) {
        if (label == null || label.isBlank()) {
            log.warn("Blank or null label received");
            throw new IllegalArgumentException("label must not be blank");
        }
    }
}
