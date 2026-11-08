-- Existing jobs have no reliable company ownership. Preserve them for explicit assignment.
-- The API requires a company for every new or edited job.
ALTER TABLE jobs ADD COLUMN company_id BIGINT REFERENCES companies(id);
CREATE INDEX idx_jobs_company ON jobs(company_id);
ALTER TABLE jobs ADD CONSTRAINT uq_job_id_company UNIQUE (id, company_id);
ALTER TABLE jobs ADD CONSTRAINT fk_job_parent_company
    FOREIGN KEY (parent_id, company_id) REFERENCES jobs(id, company_id);
