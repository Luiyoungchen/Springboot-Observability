
package com.angamba.observability.Controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ExchangeController {

    private static final Logger logger =
            LoggerFactory.getLogger(ExchangeController.class);

    private final RestClient restClient = RestClient.create();

    @GetMapping("/api/exchange")
    public Map<String, Object> getExchangeRate(
            @RequestParam(defaultValue = "USD") String base,
            @RequestParam(defaultValue = "INR") String target) {

        base = base.toUpperCase().trim();
        target = target.toUpperCase().trim();

        if (!base.matches("[A-Z]{3}") || !target.matches("[A-Z]{3}")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Currency codes must contain three letters");
        }

        logger.info("Exchange API requested: base={}, target={}", base, target);

        try {
            Map<?, ?> response = restClient.get()
                    .uri("https://api.frankfurter.dev/v2/rate/{base}/{target}",
                            base, target)
                    .retrieve()
                    .body(Map.class);

            if (response == null || response.get("rate") == null) {
                throw new RestClientException("Exchange rate was not returned");
            }

            logger.info("Exchange API completed: base={}, target={}",
                    base, target);

            return Map.of(
                    "base", base,
                    "target", target,
                    "rate", response.get("rate"),
                    "date", response.get("date"),
                    "source", "Frankfurter"
            );

        } catch (RestClientException exception) {
            logger.error("Exchange provider request failed: {} to {}",
                    base, target, exception);

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Exchange rate provider is unavailable or the currency pair is unsupported",
                    exception);
        }
    }
}
