package com.luwei.ordering.bootstrap;

import com.luwei.ordering.entity.UserDishFeedbackEntity;
import com.luwei.ordering.mapper.UserDishFeedbackMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeedbackDataSeeder implements CommandLineRunner {
    private final UserDishFeedbackMapper feedbackMapper;

    @Override
    public void run(String... args) {
        if (feedbackMapper.countAll() > 0) {
            log.info("反馈数据已存在,跳过灌入");
            return;
        }

        // 用户1：重口味，偏爱麻婆豆腐、口水鸡
        seedUser(1L, Map.of(
                11L, new Integer[]{5, 5, 5, 4, 5},  // 麻婆豆腐：最爱
                7L,  new Integer[]{4, 5, 4},        // 口水鸡：喜欢
                3L,  new Integer[]{3, 3},           // 水煮牛肉：一般
                9L,  new Integer[]{2},              // 香辣虾：不喜欢
                6L,  new Integer[]{2}               // 清蒸鲈鱼：不爱清淡
        ));

        // 用户2：清淡口，偏爱清蒸鲈鱼、白切鸡
        seedUser(2L, Map.of(
                6L,  new Integer[]{5, 5, 5, 5, 4},  // 清蒸鲈鱼：最爱
                10L, new Integer[]{4, 5, 4, 5},     // 白切鸡：喜欢
                12L, new Integer[]{4, 4, 3},        // 蒜蓉西兰花：喜欢
                4L,  new Integer[]{3, 3},           // 红烧肉：一般
                3L,  new Integer[]{1},              // 水煮牛肉：讨厌（太辣）
                9L,  new Integer[]{1},              // 香辣虾：讨厌（太辣）
                11L, new Integer[]{2}               // 麻婆豆腐：不喜欢
        ));

        log.info("演示反馈数据灌入完成");
    }

    private void seedUser(Long userId, Map<Long, Integer[]> seed) {
        seed.forEach((dishNumber, scores) -> {
            for (Integer score : scores) {
                UserDishFeedbackEntity e = new UserDishFeedbackEntity();
                e.setUserId(userId);
                e.setDishNumber(dishNumber);
                e.setFeedbackScore(score);
                feedbackMapper.insert(e);
            }
        });
        int count = seed.values().stream().mapToInt(s -> s.length).sum();
        log.info("已灌入 {} 条演示反馈, userId:{}", count, userId);
    }
}
