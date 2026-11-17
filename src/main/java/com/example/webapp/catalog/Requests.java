package com.example.webapp.catalog;

import jakarta.validation.Valid;
import com.example.webapp.dates.JalaliDate;
import com.example.webapp.dates.JalaliDates;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import tools.jackson.databind.annotation.JsonDeserialize;

public final class Requests {
    private Requests() {}

    public record TreeItem(
        @NotBlank @Size(max = 40) String code,
        @NotBlank @Size(max = 160) String title,
        @Positive Long parentId,
        @Size(max = 2000) String description) {}

    public record Job(
        @NotBlank @Size(max = 40) String code,
        @NotBlank @Size(max = 160) String title,
        @NotNull @Positive Long companyId,
        @Positive Long parentId,
        @Size(max = 2000) String description) {}

    public record Employee(
        @NotBlank @Size(max = 40) String personnelCode,
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName,
        @Pattern(regexp = "BELOW_DIPLOMA|DIPLOMA|ASSOCIATE|BACHELOR|MASTER|DOCTORATE") String educationLevel,
        @JalaliDate String startDate,
        @JalaliDate String birthDate,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal marineExperience,
        @Pattern(regexp = "MALE|FEMALE") String gender,
        @Size(max = 200) String bachelorMajor,
        @Size(max = 200) String bachelorUniversity,
        @Size(max = 200) String masterMajor,
        @Size(max = 200) String masterUniversity,
        @Size(max = 200) String doctoralMajor,
        @Size(max = 200) String doctoralUniversity,
        @Positive @JsonDeserialize(using = ReferenceIdDeserializer.class) Long specialityId,
        @Positive @JsonDeserialize(using = ReferenceIdDeserializer.class) Long grade,
        @JalaliDate String hireDate,
        @Pattern(regexp = "DRYASALAR|AMIRNEMAT|DRYADAR|DRYANAVARD|NAKHDA_BANI|NAKHDA_DOUM|NAKHDA_SOOM|NAKHDA_CHAHARUM|NAKHDA_PANJUM|NAKHDA_SHESHUM|NAKHDA_HAFTUM|NAKHDA_HASHTUM|NAKHDA_NOHUM|NAKHDA_DAHUM|NAKHDA_YAZDAHUM|NAKHDA_DOVAZHDAHUM|NAKHDA_SEYAZDAHUM") String lastMarineRank,
        @Size(max = 2000) String description) {
        public Employee {
            startDate = JalaliDates.normalize(startDate);
            birthDate = JalaliDates.normalize(birthDate);
            hireDate = JalaliDates.normalize(hireDate);
        }
    }

    public record Company(
        @NotBlank @Size(max = 40) String code,
        @NotBlank @Size(max = 160) String title,
        @Size(max = 2000) String description) {}

    public record ReferenceItem(
        @NotBlank @Size(max = 40) String code,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description) {}

    public record EmployeePosition(
        @NotNull @Positive Long employeeId,
        @NotNull @Positive Long companyId,
        @NotNull @Positive Long jobId,
        @Size(max = 2000) String notes) {}

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
        @Size(max = 2000) String notes,
        @PastOrPresent LocalDate startDate,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal impactCoefficient) {}

    public record EmployeeScore(
        @NotNull @Positive Long employeeId,
        @NotNull @Positive Long factorId,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal score,
        @NotNull @PastOrPresent LocalDate assessedOn,
        @Size(max = 2000) String notes) {}

    public record ScoreSheet(@NotNull List<@NotNull @Valid ScoreEntry> scores) {}

    public record ScoreEntry(
        @NotNull @Positive Long factorId,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal score) {}

    public record JobRequirement(
        @NotNull @Positive Long jobId,
        @NotNull @Positive Long factorId,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal minimumScore,
        @Size(max = 2000) String notes) {}
}
