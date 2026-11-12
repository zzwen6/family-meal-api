package com.zzwen6.familymeal.shopping;

import com.zzwen6.familymeal.common.BizException;
import com.zzwen6.familymeal.common.Enums.IngredientKind;
import com.zzwen6.familymeal.common.TimeHolder;
import com.zzwen6.familymeal.dish.Amounts;
import com.zzwen6.familymeal.dish.Dish;
import com.zzwen6.familymeal.dish.DishIngredient;
import com.zzwen6.familymeal.dish.DishRepository;
import com.zzwen6.familymeal.family.FamilyAccess;
import com.zzwen6.familymeal.family.FamilyMember;
import com.zzwen6.familymeal.menu.Menu;
import com.zzwen6.familymeal.menu.MenuItem;
import com.zzwen6.familymeal.menu.MenuRepository;
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

    public ShoppingService(ShoppingListRepository lists, MenuRepository menus, DishRepository dishes, FamilyAccess access) {
        this.lists = lists;
        this.menus = menus;
        this.dishes = dishes;
        this.access = access;
    }

    /**
     * 根据已确认菜单生成采购清单：
     * 1. 每道菜按「菜单份数 / 菜谱默认份数」缩放用量；
     * 2. 同名同单位合并累加，记录来自哪些菜；
     * 3. 家里常备（staple）的默认不买；重新生成时保留掌厨之前对同一样东西的勾选和已买状态。
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

        // 保留手动添加的条目（sourceDishes 为空）
        List<ShoppingItem> manual = list.getItems().stream().filter(i -> i.getSourceDishes() == null).toList();
        list.getItems().clear();
        int order = 0;
        for (Acc acc : merged.values()) {
            ShoppingItem it = new ShoppingItem();
            it.setList(list);
            it.setName(acc.name);
            it.setUnit(acc.unit);
            it.setAmount(acc.hasUnknown && acc.amount.signum() == 0 ? null : Amounts.friendly(acc.amount, acc.unit));
            it.setKind(acc.kind);
            it.setStaple(acc.allStaple);
            ShoppingItem prev = previous.get(it.mergeKey());
            it.setToBuy(prev != null ? prev.isToBuy() : !acc.allStaple);
            it.setPurchased(prev != null && prev.isPurchased());
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
        access.cook(familyId);
        Menu menu = menus.findByIdAndFamilyId(menuId, familyId).orElseThrow(() -> BizException.notFound("菜单"));
        if (menu.getItems().isEmpty()) throw BizException.badRequest("菜单还没有确认菜品");
        return view(generate(menu), menu);
    }

    /** toBuy 由掌厨调整；purchased（已买到）任何成员都可以勾。 */
    @Transactional
    public ItemView updateItem(Long familyId, Long itemId, UpdateItemRequest req) {
        FamilyMember me = access.member(familyId);
        ShoppingItem item = lists.findItem(familyId, itemId).orElseThrow(() -> BizException.notFound("采购项"));
        if (req.toBuy() != null && req.toBuy() != item.isToBuy()) {
            if (!me.getRole().canCook()) throw BizException.forbidden("只有掌厨可以调整买不买");
            item.setToBuy(req.toBuy());
        }
        if (req.purchased() != null) item.setPurchased(req.purchased());
        return ItemView.of(item);
    }

    @Transactional
    public ItemView addItem(Long familyId, Long menuId, AddItemRequest req) {
        access.cook(familyId);
        Menu menu = menus.findByIdAndFamilyId(menuId, familyId).orElseThrow(() -> BizException.notFound("菜单"));
        ShoppingList list = lists.findByMenuId(menu.getId()).orElseThrow(() -> BizException.notFound("采购清单"));
        ShoppingItem it = new ShoppingItem();
        it.setList(list);
        it.setName(req.name().trim());
        it.setAmount(Amounts.normalize(req.amount()));
        it.setUnit(req.unit() == null || req.unit().isBlank() ? null : req.unit().trim());
        it.setKind(IngredientKind.MAIN);
        it.setToBuy(true);
        it.setSortOrder(list.getItems().size());
        list.getItems().add(it);
        lists.saveAndFlush(list);
        return ItemView.of(it);
    }

    @Transactional
    public void deleteItem(Long familyId, Long itemId) {
        access.cook(familyId);
        ShoppingItem item = lists.findItem(familyId, itemId).orElseThrow(() -> BizException.notFound("采购项"));
        item.getList().getItems().remove(item);
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
