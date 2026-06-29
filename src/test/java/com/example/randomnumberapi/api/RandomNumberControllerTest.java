package com.example.randomnumberapi.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.storage.file=target/test-number-store")
@AutoConfigureMockMvc
class RandomNumberControllerTest {

    private static final Path TEST_STORAGE_DIR = Path.of("target/test-number-store");

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void cleanStorage() throws IOException {
        if (!Files.exists(TEST_STORAGE_DIR)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(TEST_STORAGE_DIR)) {
            paths.sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        throw new IllegalStateException("Failed to clean test storage path: " + path, e);
                    }
                });
        }
    }

    @Test
    void shouldGenerateAndRetrieveByLabel() throws Exception {
        mockMvc.perform(get("/new").param("label", "cameraA").param("min", "1").param("max", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.source").value("new"))
            .andExpect(jsonPath("$.label").value("cameraA"))
            .andExpect(jsonPath("$.number", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.number", lessThanOrEqualTo(5)));

        mockMvc.perform(get("/").param("label", "cameraA"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.source").value("stored"))
            .andExpect(jsonPath("$.label").value("cameraA"));
    }

    @Test
    void shouldRejectInvalidRange() throws Exception {
        mockMvc.perform(get("/new").param("label", "cameraB").param("min", "7").param("max", "3"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnNotFoundForUnknownLabel() throws Exception {
        mockMvc.perform(get("/").param("label", "unknown-label"))
            .andExpect(status().isNotFound());
    }
}

