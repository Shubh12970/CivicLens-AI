package com.civiclens.city;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** An internal CivicLens triage queue for a city. Not a live connection to any municipal system. */
@Entity
@Table(name = "departments")
public class Department {

    @Id
    private UUID id;
    @Column(name = "city_id", nullable = false)
    private UUID cityId;
    @Column(nullable = false)
    private String code;
    @Column(nullable = false)
    private String name;
    private String description;
    @Column(nullable = false)
    private boolean active;

    protected Department() {
    }

    public UUID getId() { return id; }
    public UUID getCityId() { return cityId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isActive() { return active; }
}
