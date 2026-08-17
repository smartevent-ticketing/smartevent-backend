package com.smartevent.ticketing.modules.event.service;

import com.smartevent.ticketing.modules.event.dto.request.VenueRequest;
import com.smartevent.ticketing.modules.event.dto.response.VenueResponse;

import java.util.List;
import java.util.UUID;

public interface VenueService {

    VenueResponse createVenue(VenueRequest request);

    List<VenueResponse> getAllActiveVenues();

    List<VenueResponse> getVenuesByCity(String city);

    VenueResponse getVenueById(UUID id);

    VenueResponse updateVenue(UUID id, VenueRequest request);

    void deleteVenue(UUID id);
}
