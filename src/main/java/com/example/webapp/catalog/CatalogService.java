package com.example.webapp.catalog;

import com.example.webapp.security.AccessControl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

@Service
@Transactional
public class CatalogService {
    private final CatalogRepository repository;
    private final AccessControl access;
    public CatalogService(CatalogRepository repository, AccessControl access) { this.repository = repository; this.access = access; }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Catalog catalog) { return repository.list(catalog); }

    @Transactional(readOnly = true)
    public Map<String, Long> counts() {
        Map<String, Long> result = new LinkedHashMap<>();
        var permissions = access.currentPermissions();
        for (Catalog catalog : Catalog.values()) if (permissions.contains(catalog.path + ".view")) result.put(catalog.path, repository.count(catalog));
        return result;
    }

    public long saveTree(Catalog catalog, Long id, Requests.TreeItem request) {
        if (catalog != Catalog.FACTORS) throw new IllegalArgumentException("Use saveJob for company-owned jobs");
        validateTree(catalog, id, request.parentId());
        return repository.save(catalog, id, fields("code", clean(request.code()), "title", clean(request.title()),
            "parent_id", request.parentId(), "description", clean(request.description())));
    }

    private void validateTree(Catalog catalog, Long id, Long parentId) {
        repository.lockTree(catalog);
        requireExisting(catalog, id);
        requireReference(catalog, parentId);
        Set<Long> visited = new HashSet<>();
        Long parent = parentId;
        while (parent != null) {
            if (parent.equals(id) || !visited.add(parent))
                throw new CatalogException(400, "والد انتخاب‌شده باعث ایجاد چرخه در ساختار می‌شود. یک والد دیگر انتخاب کنید.");
            parent = repository.parent(catalog, parent);
        }
    }

    public long saveJob(Long id, Requests.Job r) {
        validateTree(Catalog.JOBS, id, r.parentId());
        requireReference(Catalog.COMPANIES, r.companyId());
        if (r.parentId() != null && !r.companyId().equals(repository.jobCompany(r.parentId())))
            throw new CatalogException(400, "شغل بالادست باید متعلق به همین شرکت باشد. اگر شرکت شغل بالادست مشخص نیست، ابتدا آن را تعیین کنید.");
        if (id != null && repository.hasJobChildrenInOtherCompanies(id, r.companyId()))
            throw new CatalogException(409, "این شغل زیرمجموعه‌هایی در شرکت دیگری دارد. ابتدا ارتباط زیرمجموعه‌ها را اصلاح کنید.");
        if (id != null && repository.hasJobPositionsInOtherCompanies(id, r.companyId()))
            throw new CatalogException(409, "این شغل در سمت‌های کارمندانِ شرکت دیگری استفاده شده است. ابتدا آن سمت‌ها را اصلاح کنید.");
        return repository.save(Catalog.JOBS, id, fields("code", clean(r.code()), "title", clean(r.title()),
            "company_id", r.companyId(), "parent_id", r.parentId(), "description", clean(r.description())));
    }

    public long saveEmployee(Long id, Requests.Employee r) {
        requireExisting(Catalog.EMPLOYEES, id);
        return repository.save(Catalog.EMPLOYEES, id, fields("personnel_code", clean(r.personnelCode()),
            "first_name", clean(r.firstName()), "last_name", clean(r.lastName()),
            "education_level", r.educationLevel(), "start_date", r.startDate(),
            "description", clean(r.description())));
    }

    public long saveCompany(Long id, Requests.Company r) {
        requireExisting(Catalog.COMPANIES, id);
        return repository.save(Catalog.COMPANIES, id, fields("code", clean(r.code()), "title", clean(r.title()),
            "description", clean(r.description())));
    }

    public long savePosition(Long id, Requests.EmployeePosition r) {
        // Serialize company changes and assignments so they cannot create mismatched positions.
        repository.lockTree(Catalog.JOBS);
        requireExisting(Catalog.POSITIONS, id);
        requireReference(Catalog.EMPLOYEES, r.employeeId());
        requireReference(Catalog.COMPANIES, r.companyId());
        requireReference(Catalog.JOBS, r.jobId());
        Long jobCompany = repository.jobCompany(r.jobId());
        if (jobCompany == null)
            throw new CatalogException(400, "ابتدا شرکت این شغل را در بخش ساختار مشاغل مشخص کنید.");
        if (!r.companyId().equals(jobCompany))
            throw new CatalogException(400, "شغل انتخاب‌شده متعلق به شرکت انتخاب‌شده نیست.");
        return repository.save(Catalog.POSITIONS, id, fields("employee_id", r.employeeId(),
            "company_id", r.companyId(), "job_id", r.jobId(), "notes", clean(r.notes())));
    }

    public long saveCourse(Long id, Requests.Course r) {
        requireExisting(Catalog.COURSES, id);
        return repository.save(Catalog.COURSES, id, fields("code", clean(r.code()), "title", clean(r.title()),
            "provider", clean(r.provider()), "duration_hours", r.durationHours(), "description", clean(r.description())));
    }

    public long saveTraining(Long id, Requests.Training r) {
        requireExisting(Catalog.TRAININGS, id);
        requireReference(Catalog.EMPLOYEES, r.employeeId());
        requireReference(Catalog.COURSES, r.courseId());
        return repository.save(Catalog.TRAININGS, id, fields("employee_id", r.employeeId(), "course_id", r.courseId(),
            "completion_date", Date.valueOf(r.completionDate()), "score", r.score(), "certificate_number", clean(r.certificateNumber()), "notes", clean(r.notes())));
    }

    public long saveScore(Long id, Requests.EmployeeScore r) {
        requireExisting(Catalog.SCORES, id);
        requireReference(Catalog.EMPLOYEES, r.employeeId());
        requireReference(Catalog.FACTORS, r.factorId());
        return repository.save(Catalog.SCORES, id, fields("employee_id", r.employeeId(), "factor_id", r.factorId(),
            "score", r.score(), "assessed_on", Date.valueOf(r.assessedOn()), "notes", clean(r.notes())));
    }

    public long saveRequirement(Long id, Requests.JobRequirement r) {
        requireExisting(Catalog.REQUIREMENTS, id);
        requireReference(Catalog.JOBS, r.jobId());
        requireReference(Catalog.FACTORS, r.factorId());
        return repository.save(Catalog.REQUIREMENTS, id, fields("job_id", r.jobId(), "factor_id", r.factorId(),
            "minimum_score", r.minimumScore(), "notes", clean(r.notes())));
    }

    public List<Map<String, Object>> saveEmployeeScores(long employeeId, Requests.ScoreSheet request) {
        requireExisting(Catalog.EMPLOYEES, employeeId);
        Set<Long> factorIds = new HashSet<>();
        for (var entry : request.scores()) {
            if (!factorIds.add(entry.factorId()))
                throw new CatalogException(400, "هر شایستگی باید فقط یک بار در جدول ارسال شود.");
            requireReference(Catalog.FACTORS, entry.factorId());
        }
        Map<Long, Map<String, Object>> existing = new HashMap<>();
        for (var row : repository.employeeScores(employeeId))
            existing.put(((Number) row.get("factorId")).longValue(), row);
        // A mixed request is atomic: validate every actual mutation before writing any row.
        var permissions = access.currentPermissions();
        for (var entry : request.scores()) {
            var row = existing.get(entry.factorId());
            String action = entry.score() == null ? (row == null ? null : "delete") :
                row == null ? "create" : entry.score().compareTo((BigDecimal) row.get("score")) == 0 ? null : "edit";
            if (action != null && !permissions.contains("scores." + action))
                throw new org.springframework.security.access.AccessDeniedException("Score " + action + " permission required");
        }
        Date assessedOn = Date.valueOf(LocalDate.now());
        for (var entry : request.scores()) {
            var row = existing.get(entry.factorId());
            Long id = row == null ? null : ((Number) row.get("id")).longValue();
            if (entry.score() == null) {
                if (id != null) repository.delete(Catalog.SCORES, id);
            } else if (id == null) {
                repository.save(Catalog.SCORES, null, fields("employee_id", employeeId, "factor_id", entry.factorId(),
                    "score", entry.score(), "assessed_on", assessedOn));
            } else if (entry.score().compareTo((BigDecimal) row.get("score")) != 0) {
                // Updating only the score and date preserves any existing assessment notes.
                repository.save(Catalog.SCORES, id, fields("score", entry.score(), "assessed_on", assessedOn));
            }
        }
        return repository.employeeScores(employeeId);
    }

    public void delete(Catalog catalog, long id) {
        if (catalog == Catalog.FACTORS || catalog == Catalog.JOBS) repository.lockTree(catalog);
        repository.delete(catalog, id);
    }

    private void requireExisting(Catalog catalog, Long id) {
        if (id != null && !repository.exists(catalog, id)) throw new CatalogException(404, "رکورد موردنظر پیدا نشد.");
    }

    private void requireReference(Catalog catalog, Long id) {
        if (id != null && !repository.exists(catalog, id))
            throw new CatalogException(400, "یکی از گزینه‌های انتخاب‌شده دیگر وجود ندارد. صفحه را تازه‌سازی کنید.");
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.strip(); }

    private static Map<String, Object> fields(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }
}
