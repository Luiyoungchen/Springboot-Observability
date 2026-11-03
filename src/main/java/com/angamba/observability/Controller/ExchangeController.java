
package com.angamba.observability.Controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.angamba.observability.Service.ExchangeService;

@RestController
@RequestMapping("/api/exchange")
public class ExchangeController {

    private final ExchangeService exchangeService;

    public ExchangeController(ExchangeService exchangeService) {
        this.exchangeService = exchangeService;
    }

    // Fetch fresh exchange rate.
    @GetMapping
    public Map<String, Object> getExchangeRate(
            @RequestParam(defaultValue = "USD") String base,
            @RequestParam(defaultValue = "INR") String target) {

        return exchangeService.fetchExchangeRate(base, target);
    }

    // Return latest cached exchange rate.
    @GetMapping("/latest")
    public Map<String, Object> getLatestExchangeRate() {

        return exchangeService.getLatestExchangeRate();
    }
}
