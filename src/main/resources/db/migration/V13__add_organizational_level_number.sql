ALTER TABLE organizational_levels ADD COLUMN level INTEGER;
ALTER TABLE organizational_levels ADD CONSTRAINT chk_organizational_level_nonnegative
    CHECK (level IS NULL OR level >= 0);
