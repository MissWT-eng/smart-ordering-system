package com.luwei.ordering;

import com.luwei.ordering.dto.request.RecommendationRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationRequestValidationTest {

    @Test
    void shouldRejectZeroPeople() {
        // 准备：偏好合法，人数不合法
        RecommendationRequest request = new RecommendationRequest();
        request.setPreference("清淡一点");
        request.setPeopleNum(0);

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();

            // 执行校验
            var violations = validator.validate(request);

            // 检查：人数这个字段确实报错
            assertTrue(violations.stream().anyMatch(
                    v -> v.getPropertyPath().toString().equals("peopleNum")
            ));
        }
    }
}