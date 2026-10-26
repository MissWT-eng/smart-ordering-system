package com.luwei.ordering.dto.response;

import java.time.LocalDateTime;

public record ReviseDraftResponse(
        String draftId,
        LocalDateTime expireAt,
        DraftOrderResponse draft
) {

}
