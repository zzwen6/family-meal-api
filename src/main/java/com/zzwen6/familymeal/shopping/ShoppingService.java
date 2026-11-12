package com.zzwen6.familymeal.shopping;

import com.zzwen6.familymeal.common.BizException;
import com.zzwen6.familymeal.common.Enums.IngredientKind;
import com.zzwen6.familymeal.common.TimeHolder;
import com.zzwen6.familymeal.dish.Amounts;
import com.zzwen6.familymeal.dish.Dish;
import com.zzwen6.familymeal.dish.DishIngredient;
import com.zzwen6.familymeal.dish.DishRepository;
import com.zzwen6.familymeal.family.FamilyAccess;
import com.zzwen6.familymeal.common.Enums.Role;
import com.zzwen6.familymeal.family.FamilyMember;
import com.zzwen6.familymeal.inventory.InventoryItem;
import com.zzwen6.familymeal.inventory.InventoryItemRepository;
import com.zzwen6.familymeal.inventory.InventoryService;
import com.zzwen6.familymeal.inventory.Leftover;
import com.zzwen6.familymeal.inventory.LeftoverRepository;
import com.zzwen6.familymeal.menu.Menu;
import com.zzwen6.familymeal.menu.MenuItem;
import com.zzwen6.familymeal.menu.MenuRepository;
import com.zzwen6.familymeal.rotation.KitchenDuty;
import com.zzwen6.familymeal.shopping.ShoppingDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ShoppingService {
    private final ShoppingListRepository lists;
    private final MenuRepository menus;
    private final DishRepository dishes;
    private final FamilyAccess access;
    private final KitchenDuty duty;
    private final InventoryItemRepository inventory;
    private final LeftoverRepository leftovers;
    private final InventoryService inventoryService;

    public ShoppingService(ShoppingListRepository lists, MenuRepository menus, DishRepository dishes,
                           FamilyAccess access, KitchenDuty duty, InventoryItemRepository inventory,
                           LeftoverRepository leftovers, InventoryService inventoryService) {
        this.lists = lists;
        this.menus = menus;
        this.dishes = dishes;
        this.access = access;
        this.duty = duty;
        this.inventory = inventory;
        this.leftovers = leftovers;
        this.inventoryService = inventoryService;
    }

    /**
     * 根据已确认菜单生成采购清单：
     * 1. 每道菜按「菜单份数 / 菜谱默认份数」缩放用量；
     * 2. 同名同单位合并累加，记录来自哪些菜；
     * 3. 家里常备（staple）的默认不买；按名称对照库存和未用完剩菜扣减建议购买量（只计算、不改库存数量）；
     * 4. 重新生成时保留「已买」和已入账数量；「买不买」仅在用户曾手工改过时保留，否则按常备和库存对照重新计算。
     */
    @Transactional
    public ShoppingList generate(Menu menu) {
        ShoppingList list = lists.findByMenuId(menu.getId()).orElseGet(() -> {
            ShoppingList l = new ShoppingList();
            l.setFamilyId(menu.getFamilyId());
            l.setMenuId(menu.getId());
            return l;
        });
        Map<String, ShoppingItem> previous = list.getItems().stream()
                .collect(Collectors.toMap(ShoppingItem::mergeKey, Function.identity(), (a, b) -> a));

        Set<Long> dishIds = menu.getItems().stream().map(MenuItem::getDishId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Dish> dishMap = dishIds.isEmpty() ? Map.of() : dishes.findByFamilyIdAndIdIn(menu.getFamilyId(), dishIds)
                .stream().collect(Collectors.toMap(Dish::getId, Function.identity()));

        LinkedHashMap<String, Acc> merged = new LinkedHashMap<>();
        for (MenuItem mi : menu.getItems()) {
            Dish d = mi.getDishId() == null ? null : dishMap.get(mi.getDishId());
            if (d == null) continue; // 自定义菜名没有食材，掌厨可手动加
            for (DishIngredient ing : d.getIngredients()) {
                String key = ShoppingItem.key(ing.getName(), ing.getUnit());
                Acc acc = merged.computeIfAbsent(key, k -> new Acc(ing.getName().trim(), ing.getUnit()));
                BigDecimal scaled = Amounts.scaleRaw(ing.getAmount(), mi.getServings(), d.getDefaultServings());
                if (scaled == null) acc.hasUnknown = true;
                else acc.amount = acc.amount.add(scaled);
                if (ing.getKind() == IngredientKind.MAIN) acc.kind = IngredientKind.MAIN;
                acc.allStaple &= ing.isStaple();
                acc.sources.add(d.getName());
            }
        }

        List<StockOffset.PoolItem> pool = stockPool(menu.getFamilyId());
        // 本清单已入账的采购量不参与「家里已有」对照，避免刚买的又被当成库存
        for (ShoppingItem prev : previous.values()) {
            if (prev.getPostedAmount() != null) {
                StockOffset.consumeInventory(pool, prev.getName(), prev.getPostedAmount(),
                        prev.getPostedUnit() != null ? prev.getPostedUnit() : prev.getUnit());
            }
        }
        // 保留手动添加的条目（sourceDishes 为空）
        List<ShoppingItem> manual = list.getItems().stream().filter(i -> i.getSourceDishes() == null).toList();
        list.getItems().clear();
        int order = 0;
        for (Acc acc : merged.values()) {
            ShoppingItem it = new ShoppingItem();
            it.setList(list);
            it.setName(acc.name);
            it.setUnit(acc.unit);
            BigDecimal recipeAmount = acc.hasUnknown && acc.amount.signum() == 0 ? null : Amounts.friendly(acc.amount, acc.unit);
            StockOffset.Result offset = StockOffset.apply(acc.name, recipeAmount, acc.unit, pool);
            it.setAmount(offset.applied() ? offset.remaining() : recipeAmount);
            it.setKind(acc.kind);
            it.setStaple(acc.allStaple);
            it.setNote(offset.note());
            ShoppingItem prev = previous.get(it.mergeKey());
            boolean autoToBuy = !acc.allStaple && !offset.fullyCovered();
            if (prev != null && prev.isManualToBuy()) {
                it.setToBuy(prev.isToBuy());
                it.setManualToBuy(true);
            } else {
                it.setToBuy(autoToBuy);
                it.setManualToBuy(false);
            }
            it.setPurchased(prev != null && prev.isPurchased());
            it.setInventoryOffsetAmount(offset.inventoryUsed());
            if (prev != null && prev.getPostedAmount() != null) {
                it.setPostedAmount(prev.getPostedAmount());
                it.setPostedUnit(prev.getPostedUnit());
            }
            String src = String.join("、", acc.sources);
            it.setSourceDishes(src.length() > 250 ? src.substring(0, 250) + "…" : src);
            it.setSortOrder(order++);
            list.getItems().add(it);
        }
        for (ShoppingItem m : manual) {
            ShoppingItem copy = new ShoppingItem();
            copy.setList(list);
            copy.setName(m.getName());
            copy.setAmount(m.getAmount());
            copy.setUnit(m.getUnit());
            copy.setKind(m.getKind());
            copy.setStaple(false);
            copy.setToBuy(m.isToBuy());
            copy.setPurchased(m.isPurchased());
            copy.setInventoryOffsetAmount(m.getInventoryOffsetAmount());
            copy.setPostedAmount(m.getPostedAmount());
            copy.setPostedUnit(m.getPostedUnit());
            copy.setManualToBuy(m.isManualToBuy() || m.isToBuy()); // 手加的条目视为用户意图
            copy.setNote(m.getNote());
            copy.setSortOrder(order++);
            list.getItems().add(copy);
        }
        list.setGeneratedAt(TimeHolder.now());
        return lists.save(list);
    }

    @Transactional(readOnly = true)
    public ShoppingListView get(Long familyId, Long menuId) {
        access.member(familyId);
        Menu menu = menus.findByIdAndFamilyId(menuId, familyId).orElseThrow(() -> BizException.notFound("菜单"));
        ShoppingList list = lists.findByMenuId(menu.getId())
                .orElseThrow(() -> BizException.notFound("采购清单（菜单确认后自动生成）"));
        return view(list, menu);
    }

    @Transactional
    public ShoppingListView regenerate(Long familyId, Long menuId) {
        FamilyMember me = access.member(familyId);
        Menu menu = menus.findByIdAndFamilyId(menuId, familyId).orElseThrow(() -> BizException.notFound("菜单"));
        duty.requireKitchen(me, menu.getMealDate(), menu.getMealType());
        if (menu.getItems().isEmpty()) throw BizException.badRequest("菜单还没有确认菜品");
        return view(generate(menu), menu);
    }

    /** toBuy 由掌厨调整；purchased（已买到）任何成员都可以勾。 */
    @Transactional
    public ItemView updateItem(Long familyId, Long itemId, UpdateItemRequest req) {
        FamilyMember me = access.member(familyId);
        if (me.getRole() == Role.CHILD) throw BizException.forbidden("小朋友不能改采购清单");
        ShoppingItem item = lists.findItem(familyId, itemId).orElseThrow(() -> BizException.notFound("采购项"));
        if (req.toBuy() != null && req.toBuy() != item.isToBuy()) {
            Menu menu = menus.findByIdAndFamilyId(item.getList().getMenuId(), familyId)
                    .orElseThrow(() -> BizException.notFound("菜单"));
            duty.requireKitchen(me, menu.getMealDate(), menu.getMealType());
            item.setToBuy(req.toBuy());
            item.setManualToBuy(true);
        }
        if (req.purchased() != null && req.purchased() != item.isPurchased()) {
            item.setPurchased(req.purchased());
            if (req.purchased()) inventoryService.postPurchase(familyId, item);
            else inventoryService.revertPurchase(familyId, item);
        }
        return ItemView.of(item);
    }

    @Transactional
    public ItemView addItem(Long familyId, Long menuId, AddItemRequest req) {
        FamilyMember me = access.member(familyId);
        Menu menu = menus.findByIdAndFamilyId(menuId, familyId).orElseThrow(() -> BizException.notFound("菜单"));
        duty.requireKitchen(me, menu.getMealDate(), menu.getMealType());
        ShoppingList list = lists.findByMenuId(menu.getId()).orElseThrow(() -> BizException.notFound("采购清单"));
        ShoppingItem it = new ShoppingItem();
        it.setList(list);
        it.setName(req.name().trim());
        it.setAmount(Amounts.normalize(req.amount()));
        it.setUnit(req.unit() == null || req.unit().isBlank() ? null : req.unit().trim());
        it.setKind(IngredientKind.MAIN);
        it.setToBuy(true);
        it.setManualToBuy(true);
        it.setSortOrder(list.getItems().size());
        list.getItems().add(it);
        lists.saveAndFlush(list);
        return ItemView.of(it);
    }

    @Transactional
    public void deleteItem(Long familyId, Long itemId) {
        FamilyMember me = access.member(familyId);
        ShoppingItem item = lists.findItem(familyId, itemId).orElseThrow(() -> BizException.notFound("采购项"));
        Menu menu = menus.findByIdAndFamilyId(item.getList().getMenuId(), familyId)
                .orElseThrow(() -> BizException.notFound("菜单"));
        duty.requireKitchen(me, menu.getMealDate(), menu.getMealType());
        item.getList().getItems().remove(item);
    }

    /** 开饭时扣「对照时来自库存」的部分，整餐只扣一次。 */
    @Transactional
    public void deductStockOnReady(Menu menu) {
        if (menu.isStockDeducted()) return;
        menu.setStockDeducted(true);
        lists.findByMenuId(menu.getId()).ifPresent(list ->
                inventoryService.deductForMeal(menu.getFamilyId(), menu.getId(), list.getItems()));
    }

    private List<StockOffset.PoolItem> stockPool(Long familyId) {
        List<StockOffset.PoolItem> pool = new ArrayList<>();
        for (Leftover l : leftovers.findByFamilyIdAndUsedFalseOrderByExpiresOnAscIdAsc(familyId)) {
            pool.add(new StockOffset.PoolItem(StockOffset.Kind.LEFTOVER, l.getName(), l.getAmount(), l.getUnit()));
        }
        for (InventoryItem i : inventory.findByFamilyIdOrderByIdAsc(familyId)) {
            pool.add(new StockOffset.PoolItem(StockOffset.Kind.INVENTORY, i.getName(), i.getAmount(), i.getUnit()));
        }
        return pool;
    }

    private ShoppingListView view(ShoppingList list, Menu menu) {
        List<ItemView> buy = list.getItems().stream().filter(ShoppingItem::isToBuy).map(ItemView::of).toList();
        List<ItemView> staple = list.getItems().stream().filter(i -> !i.isToBuy()).map(ItemView::of).toList();
        int purchased = (int) list.getItems().stream().filter(i -> i.isToBuy() && i.isPurchased()).count();
        return new ShoppingListView(list.getId(), menu.getId(), menu.getMealDate(), menu.getMealType(),
                list.getGeneratedAt(), buy, staple, buy.size(), purchased);
    }

    private static final class Acc {
        final String name;
        final String unit;
        BigDecimal amount = BigDecimal.ZERO;
        boolean hasUnknown;
        boolean allStaple = true;
        IngredientKind kind = IngredientKind.SEASONING;
        final LinkedHashSet<String> sources = new LinkedHashSet<>();

        Acc(String name, String unit) {
            this.name = name;
            this.unit = unit;
        }
    }
}
