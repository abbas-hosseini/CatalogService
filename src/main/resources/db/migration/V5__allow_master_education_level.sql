-- Keep the database constraint in sync with the education choices in Requests and the UI.
ALTER TABLE employees DROP CONSTRAINT chk_employee_education_level;
ALTER TABLE employees ADD CONSTRAINT chk_employee_education_level
    CHECK (education_level IS NULL OR CASE education_level
        WHEN 'BELOW_DIPLOMA' THEN TRUE
        WHEN 'DIPLOMA' THEN TRUE
        WHEN 'ASSOCIATE' THEN TRUE
        WHEN 'BACHELOR' THEN TRUE
        WHEN 'MASTER' THEN TRUE
        WHEN 'DOCTORATE' THEN TRUE
        ELSE FALSE
    END);
