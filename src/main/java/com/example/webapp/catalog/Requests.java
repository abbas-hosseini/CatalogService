package com.example.webapp.catalog;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class Requests {
    private Requests() {}

    public record TreeItem(
        @NotBlank @Size(max = 40) String code,
        @NotBlank @Size(max = 160) String title,
        @Positive Long parentId,
        @Size(max = 2000) String description) {}

    public record Employee(
        @NotBlank @Size(max = 40) String personnelCode,
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName,
        @Positive Long jobId,
        @Size(max = 2000) String description) {}

    public record Course(
        @NotBlank @Size(max = 40) String code,
        @NotBlank @Size(max = 160) String title,
        @Size(max = 160) String provider,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("10000")
        @Digits(integer = 5, fraction = 2) BigDecimal durationHours,
        @Size(max = 2000) String description) {}

    public record Training(
        @NotNull @Positive Long employeeId,
        @NotNull @Positive Long courseId,
        @NotNull @PastOrPresent LocalDate completionDate,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal score,
        @Size(max = 100) String certificateNumber,
        @Size(max = 2000) String notes) {}

    public record EmployeeScore(
        @NotNull @Positive Long employeeId,
        @NotNull @Positive Long factorId,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal score,
        @NotNull @PastOrPresent LocalDate assessedOn,
        @Size(max = 2000) String notes) {}

    public record JobRequirement(
        @NotNull @Positive Long jobId,
        @NotNull @Positive Long factorId,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal minimumScore,
        @Size(max = 2000) String notes) {}
}
