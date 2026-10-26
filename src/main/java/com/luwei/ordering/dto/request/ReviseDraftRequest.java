package com.luwei.ordering.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviseDraftRequest {
    @NotNull
    @Size(min = 1, max = 20)
    private List<@NotNull @Valid OrderItemRequest> orderItems;
}
