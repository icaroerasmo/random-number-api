package com.example.randomnumberapi.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.OptionalInt;

@Slf4j
@Component
public class LatestNumberFileStore {

    private final Path filePath;

    public LatestNumberFileStore(@Value("${app.storage.file:latest-number.txt}") String fileName) {
        this.filePath = Paths.get(fileName);
        log.info("Number store initialized. Storage path: {}", filePath.toAbsolutePath());
    }

    public synchronized OptionalInt read(String label) {
        log.debug("Reading label='{}' from {}", label, filePath);
        if (!Files.exists(filePath)) {
            log.debug("Storage file does not exist yet: {}", filePath);
            return OptionalInt.empty();
        }

        Properties properties = loadProperties();
        String rawValue = properties.getProperty(label);
        if (rawValue == null || rawValue.isBlank()) {
            log.debug("Label='{}' not present in storage", label);
            return OptionalInt.empty();
        }

        try {
            int value = Integer.parseInt(rawValue.trim());
            log.debug("Read label='{}' → value={}", label, value);
            return OptionalInt.of(value);
        } catch (NumberFormatException e) {
            log.warn("Corrupt value for label='{}': '{}' - returning empty", label, rawValue);
            return OptionalInt.empty();
        }
    }

    public synchronized void write(String label, int value) {
        log.info("Writing label='{}' → value={} to {}", label, value, filePath);
        Properties properties = loadProperties();
        properties.setProperty(label, Integer.toString(value));

        try {
            try (OutputStream outputStream = Files.newOutputStream(filePath)) {
                properties.store(outputStream, "Label -> latest random number");
            }
            log.debug("Successfully wrote label='{}' → value={}", label, value);
        } catch (IOException e) {
            log.error("Failed to persist label='{}' → value={} to {}: {}", label, value, filePath, e.getMessage(), e);
            throw new IllegalStateException("Failed to persist generated number", e);
        }
    }

    private Properties loadProperties() {
        Properties properties = new Properties();
        if (!Files.exists(filePath)) {
            return properties;
        }

        try (InputStream inputStream = Files.newInputStream(filePath)) {
            properties.load(inputStream);
            log.debug("Loaded {} entries from {}", properties.size(), filePath);
            return properties;
        } catch (IOException e) {
            log.error("Failed to load storage file {}: {}", filePath, e.getMessage(), e);
            return properties;
        }
    }
}
