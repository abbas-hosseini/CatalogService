package com.example.webapp.catalog;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record EmployeeSearchRequest(
    @Positive Long specialityId,
    @Positive Long organizationalLevelId,
    @Min(0) @Max(150) Integer ageMin,
    @Min(0) @Max(150) Integer ageMax,
    @DecimalMin("0") @DecimalMax("300") @Digits(integer = 3, fraction = 2) BigDecimal experienceMin,
    @DecimalMin("0") @DecimalMax("300") @Digits(integer = 3, fraction = 2) BigDecimal experienceMax,
    @Pattern(regexp = "MALE|FEMALE") String gender,
    @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal marineExperienceMin,
    @Pattern(regexp = "BELOW_DIPLOMA|DIPLOMA|ASSOCIATE|BACHELOR|MASTER|DOCTORATE") String educationLevel,
    @Size(max = 200) String degree,
    @DecimalMin("0") @Digits(integer = 100, fraction = 2) BigDecimal assessmentScoreMin,
    @DecimalMin("0") @Digits(integer = 100, fraction = 2) BigDecimal assessmentScoreMax,
    @Min(1) @Max(1000000) Integer page,
    @Min(1) @Max(100) Integer pageSize) {

    public EmployeeSearchRequest {
        gender = clean(gender);
        educationLevel = clean(educationLevel);
        degree = clean(degree);
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 20 : pageSize;
    }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.strip(); }
}
