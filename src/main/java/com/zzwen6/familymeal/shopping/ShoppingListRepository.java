package com.zzwen6.familymeal.shopping;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ShoppingListRepository extends JpaRepository<ShoppingList, Long> {
    Optional<ShoppingList> findByMenuId(Long menuId);

    @Query("select i from ShoppingItem i where i.id = :itemId and i.list.familyId = :familyId")
    Optional<ShoppingItem> findItem(@Param("familyId") Long familyId, @Param("itemId") Long itemId);
}
