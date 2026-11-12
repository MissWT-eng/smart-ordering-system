package com.luwei.ordering.service.composer;

import com.luwei.ordering.common.enums.Category;
import com.luwei.ordering.entity.DishEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class MealComposer {

    public List<DishEntity> compose(List<DishEntity> candidates,
                                    BigDecimal budget,
                                    int dishCount) {
        // 空输入直接返回，避免后续分组和流操作抛空指针
        if (candidates == null || candidates.isEmpty() || dishCount <= 0) {
            return List.of();
        }

        // 按类分菜
        Map<Category, List<DishEntity>> byCategory = candidates.stream()
                .filter(d -> d.getCategory() != null)
                .collect(Collectors.groupingBy(
                        DishEntity::getCategory,
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<DishEntity> meats = byCategory.getOrDefault(Category.MEAT, List.of());
        List<DishEntity> veggies = byCategory.getOrDefault(Category.VEGETABLE, List.of());
        List<DishEntity> soups = byCategory.getOrDefault(Category.SOUP, List.of());
        List<DishEntity> drinks = byCategory.getOrDefault(Category.DRINK, List.of());

        // 配额：{荤, 素, 汤}，不含饮品
        int[] quota = quotaOf(dishCount);

        // 按配额分配
        Set<Long> picked = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        total = pick(meats, quota[0], picked, total, budget);
        total = pick(veggies, quota[1], picked, total, budget);
        total = pick(soups, quota[2], picked, total, budget);

        // 补位：先素（便宜、不易超预算），再荤
        int missing = dishCount - picked.size();
        if (missing > 0) {
            total = pick(veggies, missing, picked, total, budget);
        }

        missing = dishCount - picked.size();
        if (missing > 0) {
            total = pick(meats, missing, picked, total, budget);
        }

        // 饮品不占菜品名额，三道以上才配一杯
        if (dishCount >= 3) {
            total = pick(drinks, 1, picked, total, budget);
        }

        // 按原顺序返回，保证最高分仍在第一位
        List<DishEntity> ordered = candidates.stream()
                .filter(d -> picked.contains(d.getDishNumber()))
                .toList();

        // 饮品统一挪到末尾：它按分数可能插在两道菜之间，看着像上菜顺序乱了
        return Stream.concat(
                        ordered.stream().filter(d -> d.getCategory() != Category.DRINK),
                        ordered.stream().filter(d -> d.getCategory() == Category.DRINK))
                .toList();
    }

    private int[] quotaOf(int n) {
        return switch (n) {
            case 1 -> new int[]{1, 0, 0};
            case 2 -> new int[]{1, 1, 0};
            case 3 -> new int[]{1, 1, 1};
            case 4 -> new int[]{2, 1, 1};
            case 5 -> new int[]{2, 2, 1};
            case 6 -> new int[]{2, 3, 1};
            case 7 -> new int[]{3, 3, 1};
            default -> {
                // 超过 7 道按荤 40% / 素 40% / 汤 20% 估算，并保证配额合计等于 n
                int veg = (int) Math.round(n * 0.4);
                int soup = Math.max(1, (int) Math.round(n * 0.2));
                int meat = Math.max(0, n - veg - soup);
                yield new int[]{meat, veg, soup};
            }
        };
    }

    private BigDecimal pick(List<DishEntity> pool, int count,
                            Set<Long> picked, BigDecimal total, BigDecimal budget) {
        int taken = 0;
        for (DishEntity dish : pool) {
            if (taken >= count) break;
            if (picked.contains(dish.getDishNumber())) continue;

            if (budget != null) {
                BigDecimal next = total.add(dish.getPrice());
                if (next.compareTo(budget) > 0) continue;   // 这道超了，跳过看下一道
                total = next;
            }
            picked.add(dish.getDishNumber());
            taken++;
        }
        return total;
    }
}
