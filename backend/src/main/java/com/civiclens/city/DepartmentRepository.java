package com.civiclens.city;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    List<Department> findByCityIdAndActiveTrueOrderByNameAsc(UUID cityId);
}
