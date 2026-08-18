package com.smartevent.modules.event.service.impl;

import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.event.dto.request.VenueRequest;
import com.smartevent.modules.event.dto.response.VenueResponse;
import com.smartevent.modules.event.entity.Venue;
import com.smartevent.modules.event.exception.EventException;
import com.smartevent.modules.event.repository.VenueRepository;
import com.smartevent.modules.event.service.VenueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VenueServiceImpl implements VenueService {

    private final VenueRepository venueRepository;

    @Override
    @Transactional
    public VenueResponse createVenue(VenueRequest request) {

        if (venueRepository.existsByNameAndCity(request.name(), request.city())) {
            throw new EventException(ErrorCode.BUSINESS_RULE_VIOLATION, "Địa điểm đã tồn tại");
        }

        Venue venue = new Venue(
                request.name(),
                request.address(),
                request.city(),
                request.latitude(),
                request.longitude(),
                request.capacity()
        );

        Venue savedVenue = venueRepository.save(venue);

        return VenueResponse.from(savedVenue);
    }

    @Override
    @Transactional
    public List<VenueResponse> getAllActiveVenues() {

        List<Venue> venues = venueRepository.findByStatus("ACTIVE");

        return venues.stream()
                .map(VenueResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public List<VenueResponse> getVenuesByCity(String city) {

        List<Venue> venues = venueRepository.findByCityIgnoreCaseAndStatus(city, "ACTIVE");
        if (venues == null) {
            throw new EventException(
                ErrorCode.RESOURCE_NOT_FOUND,
                "Không tìm thấy danh mục");
        }
        return venues.stream()
                .map(VenueResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public VenueResponse getVenueById(UUID id) {

        Venue venue = venueRepository.findById(id)
                .orElseThrow(() -> new EventException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy địa điểm"
                ));

        return VenueResponse.from(venue);
    }

    @Override
    @Transactional
    public VenueResponse updateVenue(UUID id, VenueRequest request) {

        Venue venue = venueRepository.findById(id)
                .orElseThrow(() -> new EventException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy địa điểm"
                ));

        boolean isDuplicate = venueRepository.existsByNameAndCity(request.name(), request.city());
        boolean isSameVenue = venue.getName().equals(request.name()) && venue.getCity().equals(request.city());

        if (isDuplicate && !isSameVenue) {
            throw new EventException(ErrorCode.BUSINESS_RULE_VIOLATION, "Địa điểm đã tồn tại");
        }

        venue.setName(request.name());
        venue.setAddress(request.address());
        venue.setCity(request.city());
        venue.setCapacity(request.capacity());
        venue.setLatitude(request.latitude());
        venue.setLongitude(request.longitude());

        Venue updateVenue = venueRepository.save(venue);

        return VenueResponse.from(updateVenue);

    }

    @Override
    @Transactional
    public void deleteVenue(UUID id) {

        Venue venue = venueRepository.findById(id)
                .orElseThrow(() -> new EventException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy địa điểm"));

        venue.setStatus("INACTIVE");

        venueRepository.save(venue);
    }
}

