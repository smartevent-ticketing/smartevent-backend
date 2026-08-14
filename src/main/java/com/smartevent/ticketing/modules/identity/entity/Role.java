package com.smartevent.ticketing.modules.identity.entity;

import com.smartevent.ticketing.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "roles")
public class Role extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    protected Role() {
    }

    public String getName() {
        return name;
    }
}
