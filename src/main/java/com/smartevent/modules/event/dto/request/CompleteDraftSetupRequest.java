package com.smartevent.modules.event.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CompleteDraftSetupRequest(
        @NotEmpty @Valid List<CreateEventSetupRequest.Tier> tiers
) {}
