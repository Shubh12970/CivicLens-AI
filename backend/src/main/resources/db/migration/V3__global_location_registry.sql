-- Global location registry. Existing seeded cities are retained and assigned their ISO-3166-1 alpha-2 code.
ALTER TABLE cities ADD COLUMN country_code VARCHAR(2) NOT NULL DEFAULT 'IN';
ALTER TABLE cities ADD COLUMN location_key VARCHAR(300);

UPDATE cities
SET location_key = upper(country_code) || ':' ||
    lower(regexp_replace(trim(coalesce(state, '')), '\s+', ' ', 'g')) || ':' ||
    lower(regexp_replace(trim(name), '\s+', ' ', 'g'));

ALTER TABLE cities ALTER COLUMN location_key SET NOT NULL;
CREATE UNIQUE INDEX uq_cities_location_key ON cities (location_key);
CREATE INDEX ix_cities_country_name ON cities (country_code, name);
