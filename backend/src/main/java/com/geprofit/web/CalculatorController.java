package com.geprofit.web;

import com.geprofit.decant.DecantOpportunity;
import com.geprofit.decant.DecantService;
import com.geprofit.process.AlchOpportunity;
import com.geprofit.process.ProcessingService;
import com.geprofit.process.RecipeMargin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The decanting and processing calculators. */
@RestController
@RequestMapping("/api")
@Validated
public class CalculatorController {

    private final DecantService decant;
    private final ProcessingService processing;

    public CalculatorController(DecantService decant, ProcessingService processing) {
        this.decant = decant;
        this.processing = processing;
    }

    @GetMapping("/decant")
    public List<DecantOpportunity> decant(
            @RequestParam(defaultValue = "1") long minProfitPerDose,
            @RequestParam(defaultValue = "50") @Min(1) @Max(500) int limit) {
        return decant.find(minProfitPerDose, limit);
    }

    @GetMapping("/alch")
    public List<AlchOpportunity> alch(
            @RequestParam(defaultValue = "1") long minProfit,
            @RequestParam(defaultValue = "100") long minVolume,
            @RequestParam(defaultValue = "50") @Min(1) @Max(500) int limit) {
        return processing.highAlch(minProfit, minVolume, limit);
    }

    @GetMapping("/recipes")
    public List<RecipeMargin> recipes(
            @RequestParam(defaultValue = "1") long minProfit,
            @RequestParam(defaultValue = "50") long minVolume,
            @RequestParam(defaultValue = "50") @Min(1) @Max(500) int limit) {
        return processing.recipes(minProfit, minVolume, limit);
    }
}
