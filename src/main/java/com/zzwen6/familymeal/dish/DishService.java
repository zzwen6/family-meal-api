package com.zzwen6.familymeal.dish;

import com.zzwen6.familymeal.common.BizException;
import com.zzwen6.familymeal.common.Enums.Difficulty;
import com.zzwen6.familymeal.common.Enums.IngredientKind;
import com.zzwen6.familymeal.dish.DishDtos.*;
import com.zzwen6.familymeal.family.FamilyAccess;
import com.zzwen6.familymeal.family.FamilyMember;
import com.zzwen6.familymeal.media.MediaService;
import com.zzwen6.familymeal.nutrition.NutritionDtos.NutritionOverride;
import com.zzwen6.familymeal.nutrition.NutritionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class DishService {
    private final DishRepository dishes;
    private final FamilyAccess access;
    private final NutritionService nutrition;
    private final MediaService media;

    public DishService(DishRepository dishes, FamilyAccess access, NutritionService nutrition, MediaService media) {
        this.dishes = dishes;
        this.access = access;
        this.nutrition = nutrition;
        this.media = media;
    }

    @Transactional(readOnly = true)
    public List<DishSummary> list(Long familyId, String category, String keyword, boolean includeInactive) {
        FamilyMember me = access.member(familyId);
        boolean inactive = includeInactive && me.getRole().canCook();
        return dishes.search(familyId, blankToNull(category), blankToNull(keyword), inactive)
                .stream().map(DishSummary::of).toList();
    }

    @Transactional(readOnly = true)
    public List<String> categories(Long familyId) {
        access.member(familyId);
        return dishes.categories(familyId);
    }

    @Transactional(readOnly = true)
    public DishDetail detail(Long familyId, Long dishId, Integer servings) {
        access.member(familyId);
        Dish d = find(familyId, dishId);
        return toDetail(d, servings == null || servings < 1 ? d.getDefaultServings() : servings);
    }

    @Transactional
    public DishDetail create(Long familyId, DishRequest req) {
        FamilyMember me = access.cook(familyId);
        Dish d = new Dish();
        d.setFamilyId(familyId);
        d.setCreatedBy(me.getUserId());
        apply(d, req);
        dishes.save(d);
        return toDetail(d, d.getDefaultServings());
    }

    @Transactional
    public DishDetail update(Long familyId, Long dishId, DishRequest req) {
        access.cook(familyId);
        Dish d = find(familyId, dishId);
        List<String> oldMedia = collectMedia(d);
        apply(d, req);
        dishes.saveAndFlush(d);
        media.deleteUnused(familyId, oldMedia, collectMedia(d));
        return toDetail(d, d.getDefaultServings());
    }

    /** 下架（软删除）：历史菜单和点餐仍能引用。 */
    @Transactional
    public void setActive(Long familyId, Long dishId, boolean active) {
        access.cook(familyId);
        find(familyId, dishId).setActive(active);
    }

    Dish find(Long familyId, Long dishId) {
        return dishes.findByIdAndFamilyId(dishId, familyId).orElseThrow(() -> BizException.notFound("菜品"));
    }

    private void apply(Dish d, DishRequest req) {
        d.setName(req.name().trim());
        d.setCategory(req.category().trim());
        d.setDifficulty(req.difficulty() == null ? Difficulty.MEDIUM : req.difficulty());
        d.setDurationMinutes(req.durationMinutes());
        d.setDefaultServings(req.defaultServings());
        d.setCoverUrl(blankToNull(req.coverUrl()));
        d.setDescription(blankToNull(req.description()));
        d.setTips(blankToNull(req.tips()));
        applyNutrition(d, req.nutrition());

        d.getSteps().clear();
        if (req.steps() != null) {
            int no = 1;
            for (StepInput s : req.steps()) {
                DishStep st = new DishStep();
                st.setDish(d);
                st.setStepNo(no++);
                st.setContent(s.content().trim());
                st.setDurationMinutes(s.durationMinutes());
                st.setImageUrl(blankToNull(s.imageUrl()));
                st.setVideoUrl(blankToNull(s.videoUrl()));
                st.setVideoCoverUrl(blankToNull(s.videoCoverUrl()));
                st.setVideoDurationSeconds(s.videoDurationSeconds());
                d.getSteps().add(st);
            }
        }
        d.getIngredients().clear();
        int order = 0;
        order = addIngredients(d, req.ingredients(), IngredientKind.MAIN, order);
        addIngredients(d, req.seasonings(), IngredientKind.SEASONING, order);
    }

    private int addIngredients(Dish d, List<IngredientInput> list, IngredientKind kind, int order) {
        if (list == null) return order;
        for (IngredientInput in : list) {
            DishIngredient i = new DishIngredient();
            i.setDish(d);
            i.setKind(kind);
            i.setName(in.name().trim());
            i.setAmount(in.amount());
            i.setUnit(blankToNull(in.unit()));
            i.setStaple(in.staple());
            i.setNote(blankToNull(in.note()));
            i.setSortOrder(order++);
            d.getIngredients().add(i);
        }
        return order;
    }

    public DishDetail toDetail(Dish d, int servings) {
        List<StepView> steps = d.getSteps().stream()
                .map(s -> new StepView(s.getStepNo(), s.getContent(), s.getDurationMinutes(),
                        s.getImageUrl(), s.getVideoUrl(), s.getVideoCoverUrl(), s.getVideoDurationSeconds())).toList();
        List<IngredientView> main = d.getIngredients().stream().filter(i -> i.getKind() == IngredientKind.MAIN)
                .map(i -> view(i, servings, d.getDefaultServings())).toList();
        List<IngredientView> seasonings = d.getIngredients().stream().filter(i -> i.getKind() == IngredientKind.SEASONING)
                .map(i -> view(i, servings, d.getDefaultServings())).toList();
        return new DishDetail(d.getId(), d.getName(), d.getCategory(), d.getDifficulty(), d.getDurationMinutes(),
                d.getDefaultServings(), servings, d.getCoverUrl(), d.getDescription(), d.getTips(), d.isActive(),
                steps, main, seasonings, nutrition.ofDish(d, servings), d.isNutritionOverride(), d.getUpdatedAt());
    }

    private static void applyNutrition(Dish d, NutritionOverride n) {
        if (n != null && Boolean.TRUE.equals(n.enabled())) {
            d.setNutritionOverride(true);
            d.setNutritionKcal(n.kcal());
            d.setNutritionProtein(n.protein());
            d.setNutritionFat(n.fat());
            d.setNutritionCarb(n.carb());
        } else {
            d.setNutritionOverride(false);
            d.setNutritionKcal(null);
            d.setNutritionProtein(null);
            d.setNutritionFat(null);
            d.setNutritionCarb(null);
        }
    }

    private static List<String> collectMedia(Dish d) {
        List<String> urls = new ArrayList<>();
        if (d.getCoverUrl() != null) urls.add(d.getCoverUrl());
        for (DishStep s : d.getSteps()) {
            if (s.getImageUrl() != null) urls.add(s.getImageUrl());
            if (s.getVideoUrl() != null) urls.add(s.getVideoUrl());
            if (s.getVideoCoverUrl() != null) urls.add(s.getVideoCoverUrl());
        }
        return urls;
    }

    private static IngredientView view(DishIngredient i, int servings, int def) {
        return new IngredientView(i.getId(), i.getKind(), i.getName(), Amounts.scale(i.getAmount(), servings, def, i.getUnit()),
                i.getUnit(), i.isStaple(), i.getNote());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
