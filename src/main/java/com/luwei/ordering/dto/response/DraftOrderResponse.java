package com.luwei.ordering.dto.response;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DraftOrderResponse {
    /**
     * 明细：编号、名称、单价、数量、小计。
     * 总金额。
     * 预算及是否超预算
     * */
    private List<DraftOrderItemDTO> items;

    private BigDecimal totalAmount;

    private BigDecimal budget;

    private Boolean overBudget;
}
