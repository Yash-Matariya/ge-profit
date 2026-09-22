package com.geprofit.repo;

import com.geprofit.domain.PriceHistory;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {

    List<PriceHistory> findByItemIdAndCapturedAtAfterOrderByCapturedAtAsc(Integer itemId, Instant since);

    /**
     * The average traded price for one item since a point in time.
     *
     * <p>Averaged in the database, not in Java: the alternative is pulling
     * thousands of rows across the wire to add them up. The average is of the
     * mid-point between the buy and sell sides, and rows missing either side
     * are skipped rather than counted as zero -- counting a missing price as
     * zero would silently drag every average down.
     */
    @Query("""
            SELECT AVG((h.avgHigh + h.avgLow) / 2.0)
            FROM PriceHistory h
            WHERE h.itemId = :itemId
              AND h.capturedAt >= :since
              AND h.avgHigh IS NOT NULL
              AND h.avgLow IS NOT NULL
            """)
    Double averagePriceSince(@Param("itemId") Integer itemId, @Param("since") Instant since);

    @Query("""
            SELECT COALESCE(SUM(h.highVolume + h.lowVolume), 0)
            FROM PriceHistory h
            WHERE h.itemId = :itemId AND h.capturedAt >= :since
            """)
    long totalVolumeSince(@Param("itemId") Integer itemId, @Param("since") Instant since);

    boolean existsByItemIdAndCapturedAt(Integer itemId, Instant capturedAt);

    /** Housekeeping: history older than this is not worth the disk. */
    @Query("DELETE FROM PriceHistory h WHERE h.capturedAt < :before")
    @org.springframework.data.jpa.repository.Modifying
    int deleteOlderThan(@Param("before") Instant before);
}
