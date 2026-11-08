ALTER TABLE employees ADD COLUMN education_level VARCHAR(30);
ALTER TABLE employees ADD COLUMN start_date DATE;

-- CASE avoids H2 2.4.240's cross-session CHECK (IN ...) bug.
ALTER TABLE employees ADD CONSTRAINT chk_employee_education_level
    CHECK (education_level IS NULL OR CASE education_level
        WHEN 'BELOW_DIPLOMA' THEN TRUE
        WHEN 'DIPLOMA' THEN TRUE
        WHEN 'ASSOCIATE' THEN TRUE
        WHEN 'BACHELOR' THEN TRUE
        WHEN 'DOCTORATE' THEN TRUE
        ELSE FALSE
    END);
