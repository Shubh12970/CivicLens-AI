package com.civiclens.city;

import com.civiclens.common.ApiException;
import com.civiclens.incident.IncidentCategory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Read access to cities, departments and the explicit (city, category) → department routing table. */
@Service
public class CityDirectory {

    private final CityRepository cities;
    private final DepartmentRepository departments;
    private final JdbcTemplate jdbc;

    public CityDirectory(CityRepository cities, DepartmentRepository departments, JdbcTemplate jdbc) {
        this.cities = cities;
        this.departments = departments;
        this.jdbc = jdbc;
    }

    public List<City> activeCities() {
        return cities.findByActiveTrueOrderByNameAsc();
    }

    public City requireActiveCity(UUID cityId) {
        return cities.findById(cityId).filter(City::isActive)
                .orElseThrow(() -> ApiException.notFound("City"));
    }

    public Optional<City> findCity(UUID cityId) {
        return cities.findById(cityId);
    }

    public Department requireDepartment(UUID departmentId) {
        return departments.findById(departmentId).filter(Department::isActive)
                .orElseThrow(() -> ApiException.notFound("Department"));
    }

    public Optional<Department> findDepartment(UUID departmentId) {
        return departmentId == null ? Optional.empty() : departments.findById(departmentId);
    }

    public List<Department> departmentsOf(UUID cityId) {
        return departments.findByCityIdAndActiveTrueOrderByNameAsc(cityId);
    }

    /** Department mapped to this category in this city, if the city has a mapping for it. */
    public Optional<UUID> departmentFor(UUID cityId, IncidentCategory category) {
        List<UUID> ids = jdbc.queryForList(
                "SELECT department_id FROM department_categories WHERE city_id = ? AND category = ?",
                UUID.class, cityId, category.name());
        return ids.stream().findFirst();
    }

    /** Categories each department handles, for display. */
    public Map<UUID, List<IncidentCategory>> categoriesByDepartment(UUID cityId) {
        Map<UUID, List<IncidentCategory>> result = new java.util.HashMap<>();
        jdbc.query("SELECT department_id, category FROM department_categories WHERE city_id = ? ORDER BY category",
                rs -> {
                    result.computeIfAbsent(rs.getObject("department_id", UUID.class), k -> new java.util.ArrayList<>())
                            .add(IncidentCategory.valueOf(rs.getString("category")));
                }, cityId);
        return result;
    }

    /** Category → department for one city; categories without a row are unmapped (manual triage). */
    public Map<IncidentCategory, UUID> routingTable(UUID cityId) {
        Map<IncidentCategory, UUID> map = new EnumMap<>(IncidentCategory.class);
        jdbc.query("SELECT category, department_id FROM department_categories WHERE city_id = ?",
                rs -> {
                    map.put(IncidentCategory.valueOf(rs.getString("category")), rs.getObject("department_id", UUID.class));
                }, cityId);
        return map;
    }
}
