
package com.angamba.observability.Scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.angamba.observability.Service.ExchangeService;

@Component
public class ExchangeScheduler {

    private static final Logger logger =
            LoggerFactory.getLogger(ExchangeScheduler.class);

    private final ExchangeService exchangeService;

    @Value("${app.schedulers.exchange.base:USD}")
    private String base;

    @Value("${app.schedulers.exchange.target:INR}")
    private String target;

    public ExchangeScheduler(ExchangeService exchangeService) {
        this.exchangeService = exchangeService;
    }

    @Scheduled(fixedDelayString =
            "${app.schedulers.exchange.fixed-delay:60000}")
    public void refreshExchangeData() {

        logger.info(
                "Exchange scheduler triggered | base={} | target={}",
                base, target);

        // Refresh exchange rate and record it as a scheduled execution.
        exchangeService.refreshExchangeRate(base, target);

        logger.info(
                "Exchange scheduler execution completed | base={} | target={}",
                base, target);
    }
}
