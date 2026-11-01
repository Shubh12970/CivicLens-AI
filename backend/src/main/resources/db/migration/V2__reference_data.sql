-- Reference data: supported cities, their internal triage departments, and category routing.
-- These departments are internal CivicLens triage queues configured for the demo. They are NOT connected to
-- any live municipal system, and their names do not imply an official partnership.
-- IDs are fixed so the frontend and docs can reference them reliably.

INSERT INTO cities (id, code, name, state, center_latitude, center_longitude) VALUES
 ('11111111-0000-0000-0000-000000000001', 'PUNE',      'Pune',      'Maharashtra', 18.5204, 73.8567),
 ('11111111-0000-0000-0000-000000000002', 'BENGALURU', 'Bengaluru', 'Karnataka',   12.9716, 77.5946),
 ('11111111-0000-0000-0000-000000000003', 'HYDERABAD', 'Hyderabad', 'Telangana',   17.3850, 78.4867),
 -- Nagpur intentionally has an incomplete mapping (roads + sanitation only) to exercise manual-triage routing.
 ('11111111-0000-0000-0000-000000000004', 'NAGPUR',    'Nagpur',    'Maharashtra', 21.1458, 79.0882);

-- Department id pattern: 22222222-000<city>-0000-0000-00000000000<dept>
INSERT INTO departments (id, city_id, code, name, description)
SELECT ('22222222-000' || c.n || '-0000-0000-00000000000' || d.n)::uuid,
       ('11111111-0000-0000-0000-00000000000' || c.n)::uuid,
       d.code, d.name, d.description
FROM (VALUES (1), (2), (3)) AS c(n)
CROSS JOIN (VALUES
    (1, 'ROADS',      'Roads Department',              'Road surfaces, potholes and footpaths (internal triage queue)'),
    (2, 'ELECTRICAL', 'Electrical & Streetlights',     'Streetlights, traffic signals and exposed wiring (internal triage queue)'),
    (3, 'SANITATION', 'Sanitation & Solid Waste',      'Garbage collection and dumping (internal triage queue)'),
    (4, 'WATER',      'Water Supply',                  'Pipeline leaks and supply issues (internal triage queue)'),
    (5, 'DRAINAGE',   'Drainage & Sewerage',           'Storm drains, sewage overflow and waterlogging (internal triage queue)'),
    (6, 'PARKS',      'Parks & Public Property',       'Trees, parks and public structures (internal triage queue)')
) AS d(n, code, name, description);

INSERT INTO departments (id, city_id, code, name, description) VALUES
 ('22222222-0004-0000-0000-000000000001', '11111111-0000-0000-0000-000000000004', 'ROADS', 'Roads Department',
  'Road surfaces, potholes and footpaths (internal triage queue)'),
 ('22222222-0004-0000-0000-000000000003', '11111111-0000-0000-0000-000000000004', 'SANITATION', 'Sanitation & Solid Waste',
  'Garbage collection and dumping (internal triage queue)');

INSERT INTO department_categories (city_id, category, department_id)
SELECT d.city_id, m.category, d.id
FROM departments d
JOIN (VALUES
    ('ROADS', 'ROAD_DAMAGE'),
    ('ELECTRICAL', 'STREETLIGHT'),
    ('ELECTRICAL', 'TRAFFIC_SIGNAL'),
    ('ELECTRICAL', 'ELECTRICAL_HAZARD'),
    ('SANITATION', 'GARBAGE'),
    ('WATER', 'WATER_LEAKAGE'),
    ('DRAINAGE', 'DRAINAGE'),
    ('DRAINAGE', 'SEWAGE'),
    ('DRAINAGE', 'FLOODING'),
    ('PARKS', 'TREE_FALL'),
    ('PARKS', 'PUBLIC_PROPERTY_DAMAGE')
) AS m(dept_code, category) ON m.dept_code = d.code;
