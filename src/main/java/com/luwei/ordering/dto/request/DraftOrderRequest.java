package com.luwei.ordering.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DraftOrderRequest {
    @NotNull
    @Size(min = 1, max = 20)
    private List<@NotNull @Valid OrderItemRequest> orderItems;

    @JsonProperty(required = false)
    @DecimalMin("0")
    private BigDecimal budget;
}
