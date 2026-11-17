package com.example.webapp.catalog;

import com.github.mfathi91.time.PersianDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class EmployeeSearchService {
    private final CatalogRepository repository;
    private final EmployeeAssessmentScores scores;

    public EmployeeSearchService(CatalogRepository repository, EmployeeAssessmentScores scores) {
        this.repository = repository;this.scores = scores;
    }

    public record Result(List<Map<String, Object>> rows, long total, int page, int pageSize, String asOfDate) {}

    public Result search(EmployeeSearchRequest request) {
        range(request.ageMin(), request.ageMax(), "سن");
        range(request.experienceMin(), request.experienceMax(), "سابقه");
        range(request.assessmentScoreMin(), request.assessmentScoreMax(), "برآورد موفقیت");
        if (request.specialityId() != null && !repository.exists(Catalog.SPECIALITIES, request.specialityId()))
            throw new CatalogException(400, "تخصص انتخاب‌شده وجود ندارد. گزینه‌ها را تازه‌سازی کنید.");
        Integer targetLevel = null;
        if (request.organizationalLevelId() != null) {
            if (!repository.exists(Catalog.ORGANIZATIONAL_LEVELS, request.organizationalLevelId()))
                throw new CatalogException(400, "سطح سازمانی انتخاب‌شده وجود ندارد. گزینه‌ها را تازه‌سازی کنید.");
            var selected = repository.find(Catalog.ORGANIZATIONAL_LEVELS, request.organizationalLevelId());
            if (selected.get("level") == null)
                throw new CatalogException(400, "عدد سطح سازمانی انتخاب‌شده مشخص نیست؛ ابتدا آن را در اطلاعات پایه تعیین کنید.");
            targetLevel = ((Number) selected.get("level")).intValue();
        }
        var today = PersianDate.fromGregorian(LocalDate.now(ZoneId.of("Asia/Tehran")));
        var candidates = repository.employeeSearchCandidates(request, targetLevel);
        candidates.removeIf(employee -> {
            Integer age = fullYears((String) employee.get("birthDate"), today);
            Integer workExperience = fullYears((String) employee.get("startDate"), today);
            BigDecimal marine = (BigDecimal) employee.get("marineExperience");
            BigDecimal experience = workExperience == null || marine == null ? null :
                BigDecimal.valueOf(workExperience).add(marine.multiply(new BigDecimal("1.5"))).setScale(2, RoundingMode.HALF_UP);
            employee.put("age", age);employee.put("workExperienceYears", workExperience);employee.put("experienceYears", experience);
            return !within(age, request.ageMin(), request.ageMax())
                || !within(experience, request.experienceMin(), request.experienceMax())
                || !matchesDegree(employee, request);
        });
        // Calculate only the candidates left after the other filters, using the same authoritative formula.
        scores.populate(candidates);
        candidates.removeIf(employee -> !within((BigDecimal) employee.get("assessmentScore"),
            request.assessmentScoreMin(), request.assessmentScoreMax()));
        int page = Math.min(request.page(), Math.max(1, (candidates.size() + request.pageSize() - 1) / request.pageSize()));
        int start = Math.min((page - 1) * request.pageSize(), candidates.size());
        int end = Math.min(start + request.pageSize(), candidates.size());
        return new Result(List.copyOf(candidates.subList(start, end)), candidates.size(), page, request.pageSize(), today.toString());
    }

    private static boolean matchesDegree(Map<String, Object> employee, EmployeeSearchRequest request) {
        if (request.degree() == null) return true;
        List<String> fields = request.educationLevel() == null ? List.of("bachelorMajor", "masterMajor", "doctoralMajor") :
            switch (request.educationLevel()) {
                case "BACHELOR" -> List.of("bachelorMajor");
                case "MASTER" -> List.of("masterMajor");
                case "DOCTORATE" -> List.of("doctoralMajor");
                default -> List.of();
            };
        String query = normalizeText(request.degree());
        return fields.stream().anyMatch(field -> normalizeText(Objects.toString(employee.get(field), "")).contains(query));
    }

    private static String normalizeText(String value) {
        return value.replace('ي', 'ی').replace('ك', 'ک').replace('\u200c', ' ').strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    static Integer fullYears(String date, PersianDate today) {
        if (date == null) return null;
        try {
            var start = PersianDate.parse(date);
            if (start.toGregorian().isAfter(today.toGregorian())) return null;
            int years = today.getYear() - start.getYear();
            if (today.getMonthValue() < start.getMonthValue() ||
                today.getMonthValue() == start.getMonthValue() && today.getDayOfMonth() < start.getDayOfMonth()) years--;
            return years;
        } catch (IllegalArgumentException | java.time.DateTimeException exception) {
            return null;
        }
    }

    private static <T extends Comparable<T>> boolean within(T value, T min, T max) {
        if (min == null && max == null) return true;
        return value != null && (min == null || value.compareTo(min) >= 0) && (max == null || value.compareTo(max) <= 0);
    }
    private static <T extends Comparable<T>> void range(T min, T max, String label) {
        if (min != null && max != null && min.compareTo(max) > 0)
            throw new CatalogException(400, "حداقل " + label + " نباید از حداکثر آن بیشتر باشد.");
    }
}
