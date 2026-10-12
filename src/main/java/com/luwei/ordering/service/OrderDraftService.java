package com.luwei.ordering.service;

import com.luwei.ordering.dto.request.DraftOrderRequest;
import com.luwei.ordering.dto.response.OrderDraftSnapshot;
import com.luwei.ordering.dto.response.OrderListItemDTO;

public interface OrderDraftService {
    OrderDraftSnapshot createDraft(DraftOrderRequest req, String ownerKey);

    OrderDraftSnapshot getDraft(String draftId, String ownerKey);

    OrderDraftSnapshot validateDraftForConfirmation(
            String draftId,
            String ownerKey);

    OrderListItemDTO confirmDraft(String draftId, String ownerKey);
}
