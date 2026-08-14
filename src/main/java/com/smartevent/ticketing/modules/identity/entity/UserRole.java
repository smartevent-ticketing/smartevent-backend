package com.smartevent.ticketing.modules.identity.entity;

import com.smartevent.ticketing.common.entity.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "user_roles")
public class UserRole extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    protected UserRole() {
    }

    public UserRole(User user, Role role) {
        this.user = user;
        this.role = role;
    }

    public Role getRole() {
        return role;
    }
}
