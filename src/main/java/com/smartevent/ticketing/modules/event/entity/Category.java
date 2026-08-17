package com.smartevent.ticketing.modules.event.entity;

import com.smartevent.ticketing.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "categories")
public class Category extends BaseEntity {

    @Column(name = "name",nullable = false, length = 100)
    private String name;

    @Column(name = "slug",nullable = false, length = 100, unique = true)
    private String slug;

    @Column(name = "description",columnDefinition = "TEXT")
    private String description;

    @Column(name = "status", length = 30, nullable = false)
    private String status = "ACTIVE";

    public Category(String name, String slug, String description) {
        this.name = name;
        this.slug = slug;
        this.description = description;
    }
}
