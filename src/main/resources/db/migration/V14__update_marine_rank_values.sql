-- Replace old marine rank values with new ones.
-- First, null out any existing values that are not in the new set.
UPDATE employees SET last_marine_rank = NULL
 WHERE last_marine_rank IS NOT NULL
   AND last_marine_rank NOT IN ('MASTER','CHIEF_ENGINEER','CHIEF_OFFICER','SECOND_ENGINEER','THIRD_OFFICER','THIRD_ENGINEER','FOURTH_ENGINEER','FIFTH_ENGINEER','ELECTRONIC_OFFICER','RADIO_OFFICER');

ALTER TABLE employees DROP CONSTRAINT chk_employee_last_marine_rank;

ALTER TABLE employees ADD CONSTRAINT chk_employee_last_marine_rank
    CHECK (last_marine_rank IS NULL OR CASE last_marine_rank
        WHEN 'MASTER' THEN TRUE
        WHEN 'CHIEF_ENGINEER' THEN TRUE
        WHEN 'CHIEF_OFFICER' THEN TRUE
        WHEN 'SECOND_ENGINEER' THEN TRUE
        WHEN 'THIRD_OFFICER' THEN TRUE
        WHEN 'THIRD_ENGINEER' THEN TRUE
        WHEN 'FOURTH_ENGINEER' THEN TRUE
        WHEN 'FIFTH_ENGINEER' THEN TRUE
        WHEN 'ELECTRONIC_OFFICER' THEN TRUE
        WHEN 'RADIO_OFFICER' THEN TRUE
        ELSE FALSE
    END);
