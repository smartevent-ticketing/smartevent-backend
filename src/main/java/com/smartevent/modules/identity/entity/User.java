package com.smartevent.modules.identity.entity;

import com.smartevent.common.entity.SoftDeleteEntity;
import com.smartevent.common.enums.UserStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.*;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users")
public class User extends SoftDeleteEntity {

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(length = 30)
    private String phone;

    @Column(name = "avatar_file_id")
    private UUID avatarFileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserStatus status = UserStatus.ACTIVE;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserRole> userRoles = new HashSet<>();

    @OneToOne(mappedBy = "user", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    private OrganizerProfile organizerProfile;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<RefreshToken> refreshTokens = new ArrayList<>();

    public User(String email, String passwordHash, String fullName, String phone) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.phone = phone;
        this.status = UserStatus.ACTIVE;
    }

    public void addRole(Role role) {
        this.userRoles.add(new UserRole(this, role));
    }

    public void removeRole(Role role) {
        this.userRoles.removeIf(ur -> ur.getRole().getName().equalsIgnoreCase(role.getName()));
    }

    public Set<String> getRoleNames() {
        if (this.userRoles == null) {
            return Collections.emptySet();
        }
        return this.userRoles.stream()
                .map(ur -> ur.getRole().getName())
                .collect(Collectors.toSet());
    }

    public boolean isActive() {
        return this.status == UserStatus.ACTIVE && !isDeleted();
    }
}

