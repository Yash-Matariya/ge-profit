package com.geprofit.web;

import com.geprofit.flip.Flip;
import com.geprofit.flip.FlipQuery;
import com.geprofit.flip.FlipService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The flip finder, over HTTP.
 *
 * <p>Both clients call this: the React dashboard and the RuneLite plugin. That
 * is the point of the split -- the arithmetic and the filtering live here once,
 * and the two front ends only render.
 *
 * <p>The endpoint is thin on purpose. It converts query parameters into a
 * {@link FlipQuery} and returns what the service computed; every decision worth
 * testing is in the service, which needs no HTTP to test.
 */
@RestController
@RequestMapping("/api/flips")
@Validated
public class FlipController {

    private final FlipService flips;

    public FlipController(FlipService flips) {
        this.flips = flips;
    }

    @GetMapping
    public List<Flip> find(
            @RequestParam(defaultValue = "1") long minMargin,
            @RequestParam(defaultValue = "100") long minVolume,
            @RequestParam(defaultValue = "0") long maxBuyPrice,
            @RequestParam(defaultValue = "0") double minRoi,
            @RequestParam(defaultValue = "false") boolean membersOnly,
            @RequestParam(defaultValue = "false") boolean freeToPlayOnly,
            @RequestParam(defaultValue = "POTENTIAL_PROFIT") FlipQuery.Sort sort,
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit) {

        return flips.find(new FlipQuery(minMargin, minVolume, maxBuyPrice, minRoi,
                membersOnly, freeToPlayOnly, sort, limit));
    }
}
