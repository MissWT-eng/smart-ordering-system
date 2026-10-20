package com.luwei.ordering.dto.request;

import com.luwei.ordering.common.enums.SpicyLevel;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserPreferenceRequest {
    private SpicyLevel spicyLevel;

    @Size(max = 200)
    private String preferenceText;
}
