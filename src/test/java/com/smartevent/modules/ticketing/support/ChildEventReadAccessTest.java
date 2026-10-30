package com.smartevent.modules.ticketing.support;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.smartevent.modules.event.entity.Event;
import com.smartevent.modules.event.repository.EventRepository;
import com.smartevent.modules.ticketing.repository.*;
import com.smartevent.common.enums.EventStatus;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class ChildEventReadAccessTest {
    @Test void deniesAnonymousDraftAndAllowsPublished() {
        var events = mock(EventRepository.class);
        var id = UUID.randomUUID();
        var event = new Event(); event.setId(id); event.setStatus(EventStatus.DRAFT);
        when(events.findById(id)).thenReturn(Optional.of(event));
        var access = new ChildEventReadAccess(new com.smartevent.modules.event.service.EventReadAccess(events, mock(com.smartevent.modules.event.repository.EventAreaRepository.class)), mock(TicketTypeRepository.class), mock(TicketSalePhaseRepository.class), Clock.systemUTC());
        assertThrows(RuntimeException.class, () -> access.requireEvent(id));
        event.setStatus(EventStatus.PUBLISHED);
        assertFalse(access.requireEvent(id));
    }
    @Test void verifiedOwnerAndAdminCanReadDraftButAnotherCustomerCannot() {
        var events = mock(EventRepository.class);
        var id = UUID.randomUUID(); var owner = UUID.randomUUID();
        var event = new Event(); event.setId(id); event.setStatus(EventStatus.DRAFT); event.setOrganizerId(owner);
        when(events.findById(id)).thenReturn(Optional.of(event));
        var access = new ChildEventReadAccess(new com.smartevent.modules.event.service.EventReadAccess(events, mock(com.smartevent.modules.event.repository.EventAreaRepository.class)), mock(TicketTypeRepository.class), mock(TicketSalePhaseRepository.class), Clock.systemUTC());
        var user = mock(com.smartevent.infrastructure.security.UserPrincipal.class);
        try {
            when(user.getId()).thenReturn(owner);
            var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(user, null, java.util.List.of()));
            org.springframework.security.core.context.SecurityContextHolder.setContext(context);
            assertTrue(access.requireEvent(id));
            when(user.getId()).thenReturn(UUID.randomUUID());
            assertThrows(RuntimeException.class, () -> access.requireEvent(id));
            context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(user, null,
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))));
            assertTrue(access.requireEvent(id));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }
}
