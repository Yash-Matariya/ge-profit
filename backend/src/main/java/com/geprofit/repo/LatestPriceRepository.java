package com.geprofit.repo;

import com.geprofit.domain.LatestPrice;
import com.geprofit.repo.projection.PricedItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LatestPriceRepository extends JpaRepository<LatestPrice, Integer> {

    /**
     * Every item that currently has both a buy and a sell price, joined to its
     * catalogue row, in one query.
     *
     * <p>This is the query the flip finder runs. It is a projection rather than
     * entities on purpose: the alternative is loading every Item and then its
     * LatestPrice one at a time, which is the N+1 problem over roughly four
     * thousand items.
     */
    @Query("""
            SELECT i.id            AS itemId,
                   i.name          AS name,
                   i.buyLimit      AS buyLimit,
                   i.highAlch      AS highAlch,
                   i.members       AS members,
                   p.low           AS buyAt,
                   p.high          AS sellAt,
                   p.highVolume    AS highVolume,
                   p.lowVolume     AS lowVolume,
                   p.updatedAt     AS updatedAt
            FROM Item i
            JOIN LatestPrice p ON p.itemId = i.id
            WHERE p.high IS NOT NULL
              AND p.low  IS NOT NULL
              AND p.high > 0
              AND p.low  > 0
            """)
    List<PricedItem> findAllPriced();
}
