package com.example.webapp.catalog;

import jakarta.validation.Validator;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

@Service
public class EmployeeExcelService {
    static final long MAX_BYTES = 5 * 1024 * 1024;
    static final int MAX_ROWS = 1000;
    private static final Map<String, String> LABELS = Map.ofEntries(
        Map.entry("personnelCode", "کد پرسنلی"), Map.entry("firstName", "نام"), Map.entry("lastName", "نام خانوادگی"),
        Map.entry("educationLevel", "مدرک تحصیلی"), Map.entry("startDate", "تاریخ شروع به کار (شمسی)"),
        Map.entry("birthDate", "تاریخ تولد (شمسی)"), Map.entry("hireDate", "تاریخ استخدام (شمسی)"),
        Map.entry("gender", "جنسیت"), Map.entry("marineExperience", "سابقه دریایی (سال)"),
        Map.entry("bachelorMajor", "رشته کارشناسی"), Map.entry("bachelorUniversity", "دانشگاه کارشناسی"),
        Map.entry("masterMajor", "رشته کارشناسی ارشد"), Map.entry("masterUniversity", "دانشگاه کارشناسی ارشد"),
        Map.entry("doctoralMajor", "رشته دکتری"), Map.entry("doctoralUniversity", "دانشگاه دکتری"),
        Map.entry("specialityId", "تخصص"), Map.entry("grade", "سطح سازمانی"),
        Map.entry("lastMarineRank", "آخرین درجه دریایی"), Map.entry("description", "توضیحات"));
    private static final Set<String> DATE_FIELDS = Set.of("startDate", "birthDate", "hireDate");
    private static final Set<String> DECIMAL_FIELDS = Set.of("marineExperience");
    private final CatalogRepository repository;
    private final CatalogService catalog;
    private final Validator validator;

    public EmployeeExcelService(CatalogRepository repository, CatalogService catalog, Validator validator) {
        this.repository = repository;
        this.catalog = catalog;
        this.validator = validator;
        ZipSecureFile.setMaxEntrySize(16L * 1024 * 1024);
    }

    public record ImportRow(int rowNumber, Requests.Employee employee, List<String> errors) {}
    public record ImportReport(List<ImportRow> rows, int validCount, int errorCount, int importedCount, String message) {}

    @Transactional(readOnly = true)
    public ImportReport preview(MultipartFile file) { return inspect(file); }

    @Transactional
    public ImportReport importEmployees(MultipartFile file) {
        // Revalidate the original file and current database at commit time; preview is not trusted.
        var report = inspect(file);
        if (report.errorCount() > 0) return report;
        for (var row : report.rows()) catalog.saveEmployee(null, row.employee());
        return new ImportReport(report.rows(), report.validCount(), 0, report.validCount(), "کارمندان با موفقیت ثبت شدند.");
    }

    private ImportReport inspect(MultipartFile file) {
        if (file.isEmpty()) throw new CatalogException(400, "فایل اکسل خالی است.");
        if (file.getSize() > MAX_BYTES) throw new CatalogException(413, "حجم فایل باید حداکثر ۵ مگابایت باشد.");
        String filename = Objects.requireNonNullElse(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
        if (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))
            throw new CatalogException(400, "فایل اکسل با پسوند xlsx یا xls انتخاب کنید.");
        List<ImportRow> rows = new ArrayList<>();
        try (var input = file.getInputStream(); var workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() == 0) throw new CatalogException(400, "فایل برگه‌ای ندارد.");
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getLastRowNum() > MAX_ROWS)
                throw new CatalogException(400, "هر فایل می‌تواند حداکثر ۱۰۰۰ ردیف پس از عنوان ستون‌ها داشته باشد.");
            var formatter = new DataFormatter(Locale.ROOT);
            var columns = columns(sheet.getRow(0), formatter);
            var specialities = referenceOptions(Catalog.SPECIALITIES);
            var levels = referenceOptions(Catalog.ORGANIZATIONAL_LEVELS);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (blank(row, formatter)) continue;
                var errors = new ArrayList<String>();
                var values = new HashMap<String, String>();
                for (var column : columns.entrySet()) {
                    String key = column.getValue();
                    Cell cell = row.getCell(column.getKey());
                    values.put(key, value(cell, formatter, key, errors));
                }
                // Extra data without a column title must never be silently discarded.
                for (Cell cell : row) {
                    if (!columns.containsKey(cell.getColumnIndex()) && !formatter.formatCellValue(cell).isBlank())
                        errors.add("برای ستون " + (cell.getColumnIndex() + 1) + " عنوان معتبر وارد کنید.");
                }
                String education = education(values.get("educationLevel"));
                var employee = new Requests.Employee(values.get("personnelCode"), values.get("firstName"),
                    values.get("lastName"), education, values.get("startDate"), values.get("birthDate"),
                    decimal(values.get("marineExperience"), "marineExperience", errors), gender(values.get("gender")),
                    values.get("bachelorMajor"), values.get("bachelorUniversity"),
                    values.get("masterMajor"), values.get("masterUniversity"),
                    values.get("doctoralMajor"), values.get("doctoralUniversity"),
                    reference(values.get("specialityId"), "specialityId", specialities, errors),
                    reference(values.get("grade"), "grade", levels, errors), values.get("hireDate"),
                    values.get("lastMarineRank") == null ? null : values.get("lastMarineRank").toUpperCase(Locale.ROOT),
                    values.get("description"));
                validator.validate(employee).stream().sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                    .forEach(v -> errors.add(LABELS.get(v.getPropertyPath().toString()) + ": " + validationMessage(
                        v.getPropertyPath().toString(), v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName())));
                rows.add(new ImportRow(i + 1, employee, errors));
            }
        } catch (CatalogException e) {
            throw e;
        } catch (EncryptedDocumentException e) {
            throw new CatalogException(400, "فایل رمزگذاری شده است. یک نسخهٔ بدون رمز بارگذاری کنید.");
        } catch (IOException | RuntimeException e) {
            throw new CatalogException(400, "فایل اکسل قابل خواندن نیست؛ فایل سالم و بدون رمز را با قالب نمونه بارگذاری کنید.");
        }
        if (rows.isEmpty()) throw new CatalogException(400, "فایل هیچ ردیف اطلاعاتی ندارد. اطلاعات را از ردیف دوم برگهٔ اول وارد کنید.");
        var frequencies = new HashMap<String, Integer>();
        for (var row : rows) {
            String code = row.employee().personnelCode();
            if (code != null) frequencies.merge(code, 1, Integer::sum);
        }
        var existing = repository.existingPersonnelCodes(frequencies.keySet());
        for (var row : rows) {
            String code = row.employee().personnelCode();
            if (code == null) continue;
            if (frequencies.getOrDefault(code, 0) > 1) row.errors().add("کد پرسنلی در همین فایل تکرار شده است.");
            if (existing.contains(code)) row.errors().add("این کد پرسنلی قبلاً در سامانه ثبت شده است.");
        }
        int invalid = (int) rows.stream().filter(r -> !r.errors().isEmpty()).count();
        var immutableRows = rows.stream().map(r -> new ImportRow(r.rowNumber(), r.employee(), List.copyOf(r.errors()))).toList();
        return new ImportReport(immutableRows, rows.size() - invalid, invalid, 0,
            invalid == 0 ? "اطلاعات آمادهٔ ثبت است. پیش‌نمایش را بررسی و ثبت را تأیید کنید." : "خطاهای فایل را اصلاح و دوباره بررسی کنید؛ هیچ ردیفی ذخیره نشده است.");
    }

    private static Map<Integer, String> columns(Row header, DataFormatter formatter) {
        if (header == null || header.getLastCellNum() > 20) throw new CatalogException(400, "عنوان ستون‌ها در ردیف اول و مطابق قالب نمونه باشد.");
        var columns = new LinkedHashMap<Integer, String>();
        for (Cell cell : header) {
            String title = formatter.formatCellValue(cell).strip();
            if (title.isBlank()) continue;
            String field = switch (normalizeLabel(title)) {
                case "کدپرسنلی", "personnelcode" -> "personnelCode";
                case "نام", "firstname" -> "firstName";
                case "نامخانوادگی", "lastname" -> "lastName";
                case "مدرکتحصیلی", "educationlevel" -> "educationLevel";
                case "تاریخشروعبهکار(شمسی)", "تاریخشروعبهکار", "startdate" -> "startDate";
                case "تاریختولد(شمسی)", "تاریختولد", "birthdate" -> "birthDate";
                case "تاریخاستخدام(شمسی)", "تاریخاستخدام", "hiredate" -> "hireDate";
                case "جنسیت", "gender" -> "gender";
                case "سابقهدریایی(سال)", "سابقهدریایی", "marineexperience" -> "marineExperience";
                case "رشتهکارشناسی", "bachelormajor" -> "bachelorMajor";
                case "دانشگاهکارشناسی", "bacheloruniversity" -> "bachelorUniversity";
                case "رشتهکارشناسیارشد", "mastermajor" -> "masterMajor";
                case "دانشگاهکارشناسیارشد", "masteruniversity" -> "masterUniversity";
                case "رشتهدکتری", "doctoralmajor" -> "doctoralMajor";
                case "دانشگاهدکتری", "doctoraluniversity" -> "doctoralUniversity";
                case "تخصص", "کدتخصص", "speciality", "specialityid", "specialitycode" -> "specialityId";
                case "سطحسازمانی", "کدسطحسازمانی", "مقطعتحصیلی", "grade", "organizationallevelcode" -> "grade";
                case "آخریندرجهدریایی", "lastmarinerank" -> "lastMarineRank";
                case "توضیحات", "description" -> "description";
                default -> throw new CatalogException(400, "عنوان ستون " + (cell.getColumnIndex() + 1) + " شناخته نشد. از قالب نمونه استفاده کنید.");
            };
            if (columns.containsValue(field)) throw new CatalogException(400, "ستون «" + LABELS.get(field) + "» بیش از یک بار آمده است.");
            columns.put(cell.getColumnIndex(), field);
        }
        if (!columns.values().containsAll(List.of("personnelCode", "firstName", "lastName")))
            throw new CatalogException(400, "ستون‌های کد پرسنلی، نام و نام خانوادگی الزامی‌اند.");
        return columns;
    }

    private static boolean blank(Row row, DataFormatter formatter) {
        if (row == null) return true;
        for (Cell cell : row) if (!formatter.formatCellValue(cell).isBlank()) return false;
        return true;
    }

    private static String value(Cell cell, DataFormatter formatter, String field, List<String> errors) {
        if (cell == null || cell.getCellType() == CellType.BLANK) return null;
        if (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR || cell.getCellType() == CellType.BOOLEAN) {
            errors.add(LABELS.get(field) + ": مقدار مستقیم وارد کنید؛ فرمول، خطای اکسل و مقدار منطقی مجاز نیست.");
            return null;
        }
        if (DATE_FIELDS.contains(field) && cell.getCellType() == CellType.NUMERIC) {
            errors.add(LABELS.get(field) + ": باید متن شمسی مانند ۱۴۰۳/۰۱/۰۱ باشد؛ قالب سلول را Text قرار دهید.");
            return null;
        }
        if (DECIMAL_FIELDS.contains(field) && cell.getCellType() == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                errors.add(LABELS.get(field) + ": عدد وارد کنید؛ تاریخ اکسل مجاز نیست.");
                return null;
            }
            // Validate the stored number, even when Excel's display format rounds it.
            return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
        }
        if (field.equals("personnelCode") && cell.getCellType() == CellType.NUMERIC &&
            (cell.getNumericCellValue() < 0 || cell.getNumericCellValue() != Math.rint(cell.getNumericCellValue()) || cell.getNumericCellValue() >= 1e15))
            errors.add("کد پرسنلی را به‌صورت متن وارد کنید تا دقت ارقام و صفرهای ابتدایی حفظ شوند.");
        String result = formatter.formatCellValue(cell).strip();
        return result.isEmpty() ? null : result;
    }

    private static String normalizeLabel(String value) {
        return value.replace('ي', 'ی').replace('ك', 'ک').replace("\u200c", "").replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private Map<String, Set<Long>> referenceOptions(Catalog catalog) {
        var options = new HashMap<String, Set<Long>>();
        for (var row : repository.list(catalog)) {
            long id = ((Number) row.get("id")).longValue();
            String code = row.get("code").toString(), title = row.get("title").toString();
            for (String key : List.of(code, title, code + " — " + title))
                options.computeIfAbsent(referenceKey(key), ignored -> new HashSet<>()).add(id);
        }
        return options;
    }
    private static String referenceKey(String value) {
        return value.strip().replace('ي', 'ی').replace('ك', 'ک').toLowerCase(Locale.ROOT);
    }
    private static Long reference(String value, String field, Map<String, Set<Long>> options, List<String> errors) {
        if (value == null) return null;
        var matches = options.getOrDefault(referenceKey(value), Set.of());
        if (matches.size() == 1) return matches.iterator().next();
        errors.add(LABELS.get(field) + (matches.isEmpty() ?
            ": این گزینه در اطلاعات پایه تعریف نشده است. ابتدا آن را تعریف و قالب تازه دریافت کنید." :
            ": این مقدار به چند گزینه اشاره می‌کند؛ گزینهٔ کامل «کد — عنوان» را از قالب تازه انتخاب کنید."));
        return null;
    }

    private static String education(String value) {
        if (value == null) return null;
        return switch (normalizeLabel(value)) {
            case "زیردیپلم", "below_diploma" -> "BELOW_DIPLOMA";
            case "دیپلم", "diploma" -> "DIPLOMA";
            case "فوقدیپلم", "کاردانی", "associate" -> "ASSOCIATE";
            case "لیسانس", "کارشناسی", "bachelor" -> "BACHELOR";
            case "فوقلیسانس", "کارشناسیارشد", "master" -> "MASTER";
            case "دکتری", "دکترا", "doctorate" -> "DOCTORATE";
            default -> value;
        };
    }

    private static BigDecimal decimal(String value, String field, List<String> errors) {
        if (value == null) return null;
        var normalized = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (c >= '۰' && c <= '۹') normalized.append((char) ('0' + c - '۰'));
            else if (c >= '٠' && c <= '٩') normalized.append((char) ('0' + c - '٠'));
            else normalized.append(c == '٫' ? '.' : c);
        }
        try {
            if (!normalized.toString().matches("[+-]?[0-9]+(?:\\.[0-9]+)?")) throw new NumberFormatException();
            return new BigDecimal(normalized.toString()).stripTrailingZeros();
        } catch (NumberFormatException e) {
            errors.add(LABELS.get(field) + ": عدد معتبر مانند ۱۲٫۵ وارد کنید.");
            return null;
        }
    }

    private static String gender(String value) {
        if (value == null) return null;
        return switch (normalizeLabel(value)) {
            case "مرد", "male" -> "MALE";
            case "زن", "female" -> "FEMALE";
            default -> value;
        };
    }

    private static String validationMessage(String field, String constraint) {
        return switch (constraint) {
            case "NotBlank" -> "تکمیل این مقدار الزامی است.";
            case "Size" -> "طول متن بیشتر از حد مجاز است.";
            case "Pattern" -> switch (field) {
                case "gender" -> "مرد یا زن (MALE یا FEMALE) وارد کنید.";
                case "lastMarineRank" -> "یکی از کدهای درجه دریایی درج‌شده در قالب نمونه را وارد کنید.";
                default -> "یکی از مدارک تحصیلی درج‌شده در قالب نمونه را وارد کنید.";
            };
            case "DecimalMin", "DecimalMax" -> "عدد باید بین ۰ و ۱۰۰ باشد.";
            case "Digits" -> "عدد بین ۰ و ۱۰۰ و با حداکثر دو رقم اعشار وارد کنید.";
            case "JalaliDate" -> "تاریخ شمسی معتبر با قالب ۱۴۰۳/۰۱/۰۱ وارد کنید (سال ۰۰۰۱ تا ۱۹۹۹).";
            default -> "مقدار معتبر نیست.";
        };
    }
}
