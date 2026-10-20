package com.luwei.ordering.dto.response;

import com.luwei.ordering.common.enums.SpicyLevel;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferenceProposalDTO {
    private SpicyLevel spicyLevel;

    @Size(max = 200)
    private String preferenceText;
}
