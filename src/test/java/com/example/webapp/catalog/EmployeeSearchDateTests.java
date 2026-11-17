package com.example.webapp.catalog;

import com.github.mfathi91.time.PersianDate;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EmployeeSearchDateTests {
    @Test void fullYearsUsePersianAnniversaryAndDoNotTurnMissingOrFutureDatesIntoZero() {
        var today = PersianDate.of(1405, 8, 26);
        assertThat(EmployeeSearchService.fullYears("1375-08-26", today)).isEqualTo(30);
        assertThat(EmployeeSearchService.fullYears("1375-08-27", today)).isEqualTo(29);
        assertThat(EmployeeSearchService.fullYears("1405-08-26", today)).isZero();
        assertThat(EmployeeSearchService.fullYears("1405-08-27", today)).isNull();
        assertThat(EmployeeSearchService.fullYears(null, today)).isNull();
        assertThat(EmployeeSearchService.fullYears("bad-date", today)).isNull();
        assertThat(EmployeeSearchService.fullYears("1399-12-30", PersianDate.of(1400, 12, 29))).isZero();
        assertThat(EmployeeSearchService.fullYears("1399-12-30", PersianDate.of(1401, 1, 1))).isEqualTo(1);
    }
}
