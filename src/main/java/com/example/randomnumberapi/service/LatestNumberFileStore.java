package com.example.randomnumberapi.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Properties;
import java.util.OptionalInt;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import jakarta.annotation.PreDestroy;

@Slf4j
@Component
public class LatestNumberFileStore {

    private static final Duration RETENTION_WINDOW = Duration.ofHours(24);
    private static final String ENTRY_SEPARATOR = "|";
    private static final Pattern DAILY_FILE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}\\.properties");

    private final Path storageDirectory;
    private final Clock clock;
    private final ExecutorService cleanupExecutor;
    private final Object ioLock = new Object();

    @Autowired
    public LatestNumberFileStore(@Value("${app.storage.file:numbers}") String storageLocation) {
        this(storageLocation, Clock.systemUTC());
    }

    LatestNumberFileStore(String storageLocation, Clock clock) {
        this.clock = clock;
        this.cleanupExecutor = Executors.newVirtualThreadPerTaskExecutor();
        Path configuredPath = Paths.get(storageLocation);
        this.storageDirectory = isLikelyFilePath(configuredPath)
            ? Optional.ofNullable(configuredPath.getParent()).orElse(Paths.get("."))
            : configuredPath;
        log.info("Number store initialized. Storage directory: {}", storageDirectory.toAbsolutePath());
    }

    public OptionalInt read(String label) {
        synchronized (ioLock) {
            log.debug("Reading latest value for label='{}' from {}", label, storageDirectory);
            Optional<StoredEntry> latestRecord = findLatestRecord(label);
            if (latestRecord.isEmpty()) {
                log.debug("Label='{}' not present in daily storage files", label);
                return OptionalInt.empty();
            }

            Instant now = Instant.now(clock);
            StoredEntry storedEntry = latestRecord.get();
            if (storedEntry.isExpired(now)) {
                log.debug("Label='{}' exists but is older than 24h", label);
                return OptionalInt.empty();
            }

            log.debug("Read label='{}' -> value={} timestamp={}", label, storedEntry.value(), storedEntry.timestamp());
            return OptionalInt.of(storedEntry.value());
        }
    }

    public void write(String label, int value) {
        synchronized (ioLock) {
            Instant now = Instant.now(clock);
            Path dailyFilePath = dailyFilePath(LocalDate.now(clock));
            log.info("Writing label='{}' -> value={} to {}", label, value, dailyFilePath);
            Properties properties = loadProperties(dailyFilePath);
            properties.setProperty(label, value + ENTRY_SEPARATOR + now.toEpochMilli());
            storeProperties(dailyFilePath, properties, "Label -> latest random number with timestamp");
        }
    }

    public void triggerCleanupAsync() {
        CompletableFuture.runAsync(this::cleanupExpiredRecords, cleanupExecutor)
            .exceptionally(exception -> {
                log.error("Asynchronous cleanup failed: {}", exception.getMessage(), exception);
                return null;
            });
    }

    @PreDestroy
    void shutdownCleanupExecutor() {
        cleanupExecutor.shutdown();
    }

    private void cleanupExpiredRecords() {
        synchronized (ioLock) {
            Instant now = Instant.now(clock);
            log.debug("Starting cleanup for records older than 24h in {}", storageDirectory);
            for (Path path : listDailyFiles()) {
                Properties properties = loadProperties(path);
                boolean changed = false;

                for (String key : properties.stringPropertyNames().toArray(new String[0])) {
                    String rawValue = properties.getProperty(key);
                    Optional<StoredEntry> parsed = parseEntry(rawValue);
                    if (parsed.isEmpty() || parsed.get().isExpired(now)) {
                        properties.remove(key);
                        changed = true;
                    }
                }

                if (properties.isEmpty()) {
                    deleteFileIfExists(path);
                    continue;
                }

                if (changed) {
                    storeProperties(path, properties, "Label -> latest random number with timestamp");
                }
            }
        }
    }

    private Optional<StoredEntry> findLatestRecord(String label) {
        Optional<StoredEntry> latest = Optional.empty();
        for (Path path : listDailyFiles()) {
            String rawValue = loadProperties(path).getProperty(label);
            if (rawValue == null || rawValue.isBlank()) {
                continue;
            }

            Optional<StoredEntry> parsed = parseEntry(rawValue);
            if (parsed.isEmpty()) {
                continue;
            }

            StoredEntry candidate = parsed.get();
            if (latest.isEmpty() || candidate.timestamp().isAfter(latest.get().timestamp())) {
                latest = Optional.of(candidate);
            }
        }

        return latest;
    }

    private Optional<StoredEntry> parseEntry(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Optional.empty();
        }

        String[] parts = rawValue.trim().split("\\|", 2);
        if (parts.length != 2) {
            return Optional.empty();
        }

        try {
            int value = Integer.parseInt(parts[0].trim());
            long epochMillis = Long.parseLong(parts[1].trim());
            return Optional.of(new StoredEntry(value, Instant.ofEpochMilli(epochMillis)));
        } catch (NumberFormatException e) {
            log.warn("Corrupt stored entry '{}': {}", rawValue, e.getMessage());
            return Optional.empty();
        }
    }

    private Path dailyFilePath(LocalDate date) {
        return storageDirectory.resolve(date + ".properties");
    }

    private List<Path> listDailyFiles() {
        if (!Files.exists(storageDirectory) || !Files.isDirectory(storageDirectory)) {
            return List.of();
        }

        try (Stream<Path> fileStream = Files.list(storageDirectory)) {
            return fileStream
                .filter(Files::isRegularFile)
                .filter(path -> DAILY_FILE_PATTERN.matcher(path.getFileName().toString()).matches())
                .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list daily storage files", e);
        }
    }

    private Properties loadProperties(Path path) {
        Properties properties = new Properties();
        if (!Files.exists(path)) {
            return properties;
        }

        try (InputStream inputStream = Files.newInputStream(path)) {
            properties.load(inputStream);
            log.debug("Loaded {} entries from {}", properties.size(), path);
            return properties;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load storage file: " + path, e);
        }
    }

    private void storeProperties(Path path, Properties properties, String headerComment) {
        try {
            Files.createDirectories(storageDirectory);
            try (OutputStream outputStream = Files.newOutputStream(path)) {
                properties.store(outputStream, headerComment);
            }
            log.debug("Persisted {} entries to {}", properties.size(), path);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist numbers to " + path, e);
        }
    }

    private void deleteFileIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
            log.debug("Deleted empty daily file {}", path);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete empty storage file: " + path, e);
        }
    }

    private boolean isLikelyFilePath(Path path) {
        Path fileName = path.getFileName();
        if (fileName == null) {
            return false;
        }
        return fileName.toString().contains(".");
    }

    private record StoredEntry(int value, Instant timestamp) {
        private boolean isExpired(Instant now) {
            return timestamp.plus(RETENTION_WINDOW).isBefore(now);
        }
    }
}
