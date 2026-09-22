package com.geprofit.repo;

import com.geprofit.domain.Item;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemRepository extends JpaRepository<Item, Integer> {

    @Query("SELECT i FROM Item i WHERE lower(i.name) LIKE lower(concat('%', :q, '%')) ORDER BY i.name")
    List<Item> search(@Param("q") String q);

    /** Items whose name ends in a dose suffix, e.g. "Prayer potion(4)". */
    @Query("SELECT i FROM Item i WHERE i.name LIKE '%(1)' OR i.name LIKE '%(2)' "
            + "OR i.name LIKE '%(3)' OR i.name LIKE '%(4)'")
    List<Item> findDosedItems();

    /** Items that can be alched for more than nothing. */
    @Query("SELECT i FROM Item i WHERE i.highAlch IS NOT NULL AND i.highAlch > 0")
    List<Item> findAlchable();
}
