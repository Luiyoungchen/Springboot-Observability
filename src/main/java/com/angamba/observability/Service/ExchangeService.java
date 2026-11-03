
package com.angamba.observability.Service;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import io.micrometer.core.instrument.MeterRegistry;

@Service
public class ExchangeService {

    private static final Logger logger =
            LoggerFactory.getLogger(ExchangeService.class);

    private final RestClient restClient = RestClient.create();

    private final MeterRegistry meterRegistry;

    private final AtomicReference<Map<String, Object>> latestExchange =
            new AtomicReference<>();

    public ExchangeService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    // Default exchange-rate request: on-demand.
    public Map<String, Object> fetchExchangeRate(
            String base, String target) {

        return fetchExchangeRate(base, target, "ON_DEMAND");
    }

    // Fetch exchange rate and record its execution source.
    public Map<String, Object> fetchExchangeRate(
            String base, String target, String source) {

        logger.info(
                "Exchange API requested: base={}, target={}",
                base, target);

        try {
            Map<?, ?> response = restClient.get()
                    .uri("https://api.frankfurter.dev/v2/rate/{base}/{target}",
                            base, target)
                    .retrieve()
                    .body(Map.class);

            if (response == null || response.get("rate") == null) {
                throw new RestClientException(
                        "Invalid exchange rate response");
            }

            Map<String, Object> result = Map.of(
                    "base", response.get("base"),
                    "target", response.get("quote"),
                    "rate", response.get("rate"),
                    "date", response.get("date"),
                    "source", "Frankfurter"
            );

            latestExchange.set(result);

            // Record successful exchange refresh.
            meterRegistry.counter(
                    "exchange_refresh",
                    "source",
                    source.toLowerCase()
            ).increment();

            logger.info(
                    "Exchange rate fetched successfully: {} -> {}",
                    base, target);

            return result;

        } catch (RestClientException exception) {

            recordFailure(source);

            logger.error(
                    "Exchange API request failed: {} -> {}",
                    base, target, exception);

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Exchange rate provider is unavailable",
                    exception);
        }
    }

    // Record failed exchange refresh.
    private void recordFailure(String source) {
        meterRegistry.counter(
                "exchange_refresh_failures",
                "source",
                source.toLowerCase()
        ).increment();
    }

    // Return latest cached exchange rate.
    public Map<String, Object> getLatestExchangeRate() {

        Map<String, Object> exchange = latestExchange.get();

        if (exchange == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Exchange rate data is not available yet.");
        }

        return exchange;
    }

    // Scheduled refresh.
    public void refreshExchangeRate(String base, String target) {

        logger.info(
                "Starting scheduled exchange refresh: {} -> {}",
                base, target);

        try {
            fetchExchangeRate(base, target, "SCHEDULED");

            logger.info(
                    "Scheduled exchange refresh completed: {} -> {}",
                    base, target);

        } catch (Exception exception) {

            logger.error(
                    "Scheduled exchange refresh failed: {} -> {}",
                    base, target, exception);
        }
    }
}
