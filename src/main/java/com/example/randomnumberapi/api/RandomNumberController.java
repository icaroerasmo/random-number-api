package com.example.randomnumberapi.api;

import com.example.randomnumberapi.service.RandomNumberService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
public class RandomNumberController {

    private final RandomNumberService randomNumberService;

    public RandomNumberController(RandomNumberService randomNumberService) {
        this.randomNumberService = randomNumberService;
    }

    @GetMapping("/")
    public RandomNumberResponse getStored(@RequestParam String label) {
        log.info("GET / - retrieving stored number for label='{}'", label);
        try {
            int number = randomNumberService.getStored(label);
            log.info("GET / - found stored number={} for label='{}'", number, label);
            return new RandomNumberResponse(label, number, "stored");
        } catch (IllegalArgumentException e) {
            log.warn("GET / - bad request for label='{}': {}", label, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (IllegalStateException e) {
            log.warn("GET / - label='{}' not found: {}", label, e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    @GetMapping("/new")
    public RandomNumberResponse generateNew(
        @RequestParam String label,
        @RequestParam int min,
        @RequestParam int max
    ) {
        log.info("GET /new - generating number for label='{}' min={} max={}", label, min, max);
        try {
            int number = randomNumberService.generateNew(label, min, max);
            log.info("GET /new - generated number={} for label='{}'", number, label);
            return new RandomNumberResponse(label, number, "new");
        } catch (IllegalArgumentException e) {
            log.warn("GET /new - bad request for label='{}': {}", label, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }
}
