package com.smartevent.modules.event.dto.response;

import java.util.List;
import java.util.Map;

public record EventSubmissionReadinessResponse(
        boolean ready,
        Map<String, Boolean> checklist,
        List<String> blockers
) {
}
