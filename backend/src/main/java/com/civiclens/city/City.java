package com.civiclens.city;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "cities")
public class City {

    @Id
    private UUID id;
    @Column(nullable = false)
    private String code;
    @Column(nullable = false)
    private String name;
    private String state;
    @Column(nullable = false)
    private String country;
    @Column(name = "center_latitude")
    private Double centerLatitude;
    @Column(name = "center_longitude")
    private Double centerLongitude;
    @Column(nullable = false)
    private boolean active;

    protected City() {
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getState() { return state; }
    public String getCountry() { return country; }
    public Double getCenterLatitude() { return centerLatitude; }
    public Double getCenterLongitude() { return centerLongitude; }
    public boolean isActive() { return active; }
}
