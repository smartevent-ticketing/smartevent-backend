package com.smartevent.ticketing.modules.identity.dto.response;

import com.smartevent.ticketing.modules.identity.entity.OrganizerProfile;

import java.util.UUID;

public record OrganizerProfileResponse(
        UUID id,
        String companyName,
        String taxCode,
        String businessAddress,
        String bankAccountStatus
) {
    public static OrganizerProfileResponse from(OrganizerProfile profile) {
        if (profile == null) {
            return null;
        }
        return new OrganizerProfileResponse(
                profile.getId(),
                profile.getCompanyName(),
                profile.getTaxCode(),
                profile.getBusinessAddress(),
                profile.getBankAccountStatus()
        );
    }
}