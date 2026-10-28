package com.example.webapp.catalog;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Date;
import java.util.*;

@Service
@Transactional
public class CatalogService {
    private final CatalogRepository repository;
    public CatalogService(CatalogRepository repository) { this.repository = repository; }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Catalog catalog) { return repository.list(catalog); }

    @Transactional(readOnly = true)
    public Map<String, Long> counts() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Catalog catalog : Catalog.values()) result.put(catalog.path, repository.count(catalog));
        return result;
    }

    public long saveTree(Catalog catalog, Long id, Requests.TreeItem request) {
        if (catalog != Catalog.FACTORS && catalog != Catalog.JOBS) throw new IllegalArgumentException("Not a tree");
        repository.lockTree(catalog);
        requireExisting(catalog, id);
        requireReference(catalog, request.parentId());
        Set<Long> visited = new HashSet<>();
        Long parent = request.parentId();
        while (parent != null) {
            if (parent.equals(id) || !visited.add(parent))
                throw new CatalogException(400, "والد انتخاب‌شده باعث ایجاد چرخه در ساختار می‌شود. یک والد دیگر انتخاب کنید.");
            parent = repository.parent(catalog, parent);
        }
        return repository.save(catalog, id, fields("code", clean(request.code()), "title", clean(request.title()),
            "parent_id", request.parentId(), "description", clean(request.description())));
    }

    public long saveEmployee(Long id, Requests.Employee r) {
        requireExisting(Catalog.EMPLOYEES, id);
        requireReference(Catalog.JOBS, r.jobId());
        return repository.save(Catalog.EMPLOYEES, id, fields("personnel_code", clean(r.personnelCode()),
            "first_name", clean(r.firstName()), "last_name", clean(r.lastName()), "job_id", r.jobId(), "description", clean(r.description())));
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
