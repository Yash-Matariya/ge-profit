package com.geprofit.web;

import com.geprofit.domain.Item;
import com.geprofit.domain.LatestPrice;
import com.geprofit.repo.ItemRepository;
import com.geprofit.repo.LatestPriceRepository;
import com.geprofit.repo.PriceHistoryRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Item lookup, current price, and the averages computed from our own history. */
@RestController
@RequestMapping("/api/items")
public class ItemController {

    private final ItemRepository items;
    private final LatestPriceRepository prices;
    private final PriceHistoryRepository history;

    public ItemController(ItemRepository items,
                          LatestPriceRepository prices,
                          PriceHistoryRepository history) {
        this.items = items;
        this.prices = prices;
        this.history = history;
    }

    @GetMapping
    public List<ItemSummary> search(@RequestParam(name = "q", defaultValue = "") String query) {
        if (query.isBlank()) {
            return List.of();
        }
        return items.search(query).stream()
                .limit(50)
                .map(i -> new ItemSummary(i.getId(), i.getName(), i.isMembers(),
                        i.getBuyLimit(), i.getHighAlch()))
                .toList();
    }

    /**
     * One item in detail, including the 1-day, 1-week and 4-week averages.
     *
     * <p>Those averages are the reason this application keeps its own history:
     * the Wiki API serves recent windows only, so a question like "is this
     * cheap compared with the last month?" can only be answered from data we
     * stored ourselves.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ItemDetail> byId(@PathVariable Integer id) {
        Item item = items.findById(id).orElse(null);
        if (item == null) {
            return ResponseEntity.notFound().build();
        }
        LatestPrice price = prices.findById(id).orElse(null);
        Instant now = Instant.now();

        return ResponseEntity.ok(new ItemDetail(
                item.getId(),
                item.getName(),
                item.getExamine(),
                item.isMembers(),
                item.getBuyLimit(),
                item.getHighAlch(),
                price == null ? null : price.buyAt(),
                price == null ? null : price.sellAt(),
                price == null ? null : price.getUpdatedAt(),
                history.averagePriceSince(id, now.minus(Duration.ofDays(1))),
                history.averagePriceSince(id, now.minus(Duration.ofDays(7))),
                history.averagePriceSince(id, now.minus(Duration.ofDays(28))),
                history.totalVolumeSince(id, now.minus(Duration.ofDays(1)))));
    }

    public record ItemSummary(int id, String name, boolean members,
                              Integer buyLimit, Integer highAlch) {
    }

    public record ItemDetail(int id,
                             String name,
                             String examine,
                             boolean members,
                             Integer buyLimit,
                             Integer highAlch,
                             Integer buyAt,
                             Integer sellAt,
                             Instant pricedAt,
                             Double averageOneDay,
                             Double averageOneWeek,
                             Double averageFourWeeks,
                             long volumeOneDay) {
    }
}
