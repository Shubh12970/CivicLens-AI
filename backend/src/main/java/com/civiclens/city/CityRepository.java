package com.civiclens.city;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CityRepository extends JpaRepository<City, UUID> {

    List<City> findByActiveTrueOrderByNameAsc();
}
