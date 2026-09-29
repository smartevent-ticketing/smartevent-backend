package com.smartevent.modules.identity.service;

import com.smartevent.common.error.BusinessException;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.identity.entity.Role;
import com.smartevent.modules.identity.entity.User;
import com.smartevent.modules.identity.repository.RoleRepository;
import com.smartevent.modules.identity.repository.UserRepository;
import com.smartevent.modules.identity.service.impl.AdminUserServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @InjectMocks private AdminUserServiceImpl service;

    private User customer(UUID id) {
        User user = new User("buyer@example.com", "hash", "Nguyen Van An", "0901234567");
        user.setId(id);
        user.addRole(new Role("CUSTOMER"));
        return user;
    }

    @Test
    void listUsersReturnsPagedAccountsWithRoles() {
        UUID id = UUID.randomUUID();
        User user = customer(id);
        when(userRepository.searchAdminUsers(eq("%an%"), any()))
                .thenReturn(new PageImpl<>(List.of(user), PageRequest.of(0, 20), 1));
        when(userRepository.findAllByIdsWithRoles(List.of(id))).thenReturn(List.of(user));

        var result = service.listUsers(" An ", 0, 20);

        assertEquals(1, result.totalElements());
        assertEquals("buyer@example.com", result.content().get(0).email());
        assertEquals(java.util.Set.of("CUSTOMER"), result.content().get(0).roles());
    }

    @Test
    void listUsersWithoutSearchUsesQueryWithNoNullableLikeParameter() {
        UUID id = UUID.randomUUID();
        User user = customer(id);
        when(userRepository.findByDeletedAtIsNull(any()))
                .thenReturn(new PageImpl<>(List.of(user), PageRequest.of(0, 20), 1));
        when(userRepository.findAllByIdsWithRoles(List.of(id))).thenReturn(List.of(user));

        var result = service.listUsers("  ", 0, 20);

        assertEquals(1, result.totalElements());
        verify(userRepository, never()).searchAdminUsers(any(), any());
    }

    @Test
    void grantRoleAddsRoleWithoutReplacingExistingRoles() {
        UUID id = UUID.randomUUID();
        User user = customer(id);
        when(userRepository.findNotDeletedByIdForRoleUpdate(id)).thenReturn(Optional.of(user));
        when(roleRepository.findByName("ORGANIZER")).thenReturn(Optional.of(new Role("ORGANIZER")));

        var updated = service.grantRole(id, "organizer", UUID.randomUUID());

        assertEquals(java.util.Set.of("CUSTOMER", "ORGANIZER"), updated.roles());
        verify(userRepository).saveAndFlush(user);
    }

    @Test
    void grantRoleIsIdempotentForExistingRole() {
        UUID id = UUID.randomUUID();
        User user = customer(id);
        when(userRepository.findNotDeletedByIdForRoleUpdate(id)).thenReturn(Optional.of(user));

        var updated = service.grantRole(id, "CUSTOMER", UUID.randomUUID());

        assertEquals(java.util.Set.of("CUSTOMER"), updated.roles());
        verifyNoInteractions(roleRepository);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void grantRoleRejectsUnknownRoleBeforeChangingUser() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.grantRole(UUID.randomUUID(), "SUPERUSER", UUID.randomUUID()));
        assertEquals(ErrorCode.VALIDATION_ERROR, error.getErrorCode());
        verifyNoInteractions(userRepository, roleRepository);
    }
}
