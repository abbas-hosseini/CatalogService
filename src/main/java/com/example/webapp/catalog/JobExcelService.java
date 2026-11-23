package com.example.webapp.catalog;

import jakarta.validation.Validator;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;

@Service
public class JobExcelService {
    static final long MAX_BYTES = 5 * 1024 * 1024;
    static final int MAX_ROWS = 1000;
    private static final Map<String, String> LABELS = Map.of(
        "code", "کد شغل", "title", "عنوان شغل", "company", "شرکت", "companyId", "شرکت",
        "parentCode", "کد شغل بالادست", "parentId", "شغل بالادست", "description", "توضیحات");
    private final CatalogRepository repository;
    private final CatalogService catalog;
    private final Validator validator;

    public JobExcelService(CatalogRepository repository, CatalogService catalog, Validator validator) {
        this.repository = repository; this.catalog = catalog; this.validator = validator;
        ZipSecureFile.setMaxEntrySize(16L * 1024 * 1024);
    }

    public record ImportRow(int rowNumber, Requests.Job job, String company, String parentCode, List<String> errors) {}
    public record ImportReport(List<ImportRow> rows, int validCount, int errorCount, int importedCount, String message) {}
    private record Inspection(ImportReport report, List<Integer> insertionOrder) {}

    @Transactional(readOnly = true)
    public ImportReport preview(MultipartFile file) { return inspect(file).report(); }

    @Transactional
    public ImportReport importJobs(MultipartFile file) {
        // Recheck current data under the same lock used by other job mutations.
        repository.lockTree(Catalog.JOBS);
        var inspection = inspect(file);
        var report = inspection.report();
        if (report.errorCount() > 0) return report;
        var byRow = new HashMap<Integer, ImportRow>();
        report.rows().forEach(row -> byRow.put(row.rowNumber(), row));
        var created = new HashMap<String, Long>();
        for (int rowNumber : inspection.insertionOrder()) {
            var row = byRow.get(rowNumber);
            var job = row.job();
            Long parentId = job.parentId();
            if (row.parentCode() != null && parentId == null) {
                parentId = created.get(codeKey(row.parentCode()));
                if (parentId == null) throw new CatalogException(409, "شغل بالادست قابل ثبت نیست؛ فایل را دوباره بررسی کنید.");
            }
            long id = catalog.saveJob(null, new Requests.Job(job.code(), job.title(), job.companyId(), parentId, job.description()));
            created.put(codeKey(job.code()), id);
        }
        return new ImportReport(report.rows(), report.validCount(), 0, report.validCount(), "مشاغل با موفقیت ثبت شدند.");
    }

    private Inspection inspect(MultipartFile file) {
        if (file.isEmpty()) throw new CatalogException(400, "فایل اکسل خالی است.");
        if (file.getSize() > MAX_BYTES) throw new CatalogException(413, "حجم فایل باید حداکثر ۵ مگابایت باشد.");
        String filename = Objects.requireNonNullElse(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
        if (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))
            throw new CatalogException(400, "فایل اکسل با پسوند xlsx یا xls انتخاب کنید.");
        var companies = companyOptions();
        List<ImportRow> rows = new ArrayList<>();
        try (var input = file.getInputStream(); var workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() == 0) throw new CatalogException(400, "فایل برگه‌ای ندارد.");
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getLastRowNum() > MAX_ROWS)
                throw new CatalogException(400, "هر فایل می‌تواند حداکثر ۱۰۰۰ ردیف پس از عنوان ستون‌ها داشته باشد.");
            var formatter = new DataFormatter(Locale.ROOT);
            var columns = columns(sheet.getRow(0), formatter);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (blank(row, formatter)) continue;
                var errors = new ArrayList<String>();
                var values = new HashMap<String, String>();
                for (var column : columns.entrySet())
                    values.put(column.getValue(), value(row.getCell(column.getKey()), formatter, column.getValue(), errors));
                for (Cell cell : row) {
                    if (!columns.containsKey(cell.getColumnIndex()) && !formatter.formatCellValue(cell).isBlank())
                        errors.add("برای ستون " + (cell.getColumnIndex() + 1) + " عنوان معتبر وارد کنید.");
                }
                Long companyId = company(values.get("company"), companies, errors);
                var job = new Requests.Job(values.get("code"), values.get("title"), companyId, null, values.get("description"));
                validator.validate(job).stream().sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                    .forEach(v -> errors.add(LABELS.get(v.getPropertyPath().toString()) + ": " +
                        (v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName().equals("Size") ?
                            "طول متن بیشتر از حد مجاز است." : "تکمیل این فیلد با مقدار معتبر الزامی است.")));
                String parentCode = values.get("parentCode");
                if (parentCode != null && parentCode.length() > 40) errors.add("کد شغل بالادست: حداکثر ۴۰ نویسه وارد کنید.");
                rows.add(new ImportRow(i + 1, job, values.get("company"), parentCode, errors));
            }
        } catch (CatalogException e) {
            throw e;
        } catch (EncryptedDocumentException e) {
            throw new CatalogException(400, "فایل رمزگذاری شده است. یک نسخهٔ بدون رمز بارگذاری کنید.");
        } catch (IOException | RuntimeException e) {
            throw new CatalogException(400, "فایل اکسل قابل خواندن نیست؛ فایل سالم و بدون رمز را با قالب نمونه بارگذاری کنید.");
        }
        if (rows.isEmpty()) throw new CatalogException(400, "فایل هیچ ردیف اطلاعاتی ندارد. اطلاعات را از ردیف دوم برگهٔ اول وارد کنید.");

        var existing = new HashMap<String, List<Map<String, Object>>>();
        for (var job : repository.list(Catalog.JOBS))
            existing.computeIfAbsent(codeKey(job.get("code").toString()), ignored -> new ArrayList<>()).add(job);
        var batch = new HashMap<String, List<ImportRow>>();
        for (var row : rows) if (row.job().code() != null)
            batch.computeIfAbsent(codeKey(row.job().code()), ignored -> new ArrayList<>()).add(row);
        var dependencies = new HashMap<Integer, Integer>();
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            String code = row.job().code();
            if (code != null) {
                if (batch.get(codeKey(code)).size() > 1) row.errors().add("کد شغل در همین فایل تکرار شده است.");
                if (existing.containsKey(codeKey(code))) row.errors().add("این کد شغل قبلاً در سامانه ثبت شده است؛ ورود اکسل فقط برای مشاغل جدید است.");
            }
            if (row.parentCode() == null) continue;
            String parentKey = codeKey(row.parentCode());
            var storedParents = existing.getOrDefault(parentKey, List.of());
            var batchParents = batch.getOrDefault(parentKey, List.of());
            if (storedParents.size() + batchParents.size() != 1) {
                row.errors().add(storedParents.isEmpty() && batchParents.isEmpty() ?
                    "کد شغل بالادست: در سامانه یا همین فایل پیدا نشد." : "کد شغل بالادست: مبهم است؛ کدهای تکراری را اصلاح کنید.");
                continue;
            }
            Long parentCompany;
            if (!storedParents.isEmpty()) {
                var parent = storedParents.getFirst();
                parentCompany = parent.get("companyId") == null ? null : ((Number) parent.get("companyId")).longValue();
                var job = row.job();
                rows.set(i, new ImportRow(row.rowNumber(), new Requests.Job(job.code(), job.title(), job.companyId(),
                    ((Number) parent.get("id")).longValue(), job.description()), row.company(), row.parentCode(), row.errors()));
            } else {
                var parent = batchParents.getFirst();
                parentCompany = parent.job().companyId();
                dependencies.put(row.rowNumber(), parent.rowNumber());
            }
            if (row.job().companyId() != null && !row.job().companyId().equals(parentCompany))
                row.errors().add("شغل بالادست باید متعلق به همین شرکت باشد؛ شرکت نامشخص نیز پذیرفته نیست.");
        }
        // Iterative topological ordering supports parents below children and detects cyclic ancestry.
        var byRow = new LinkedHashMap<Integer, ImportRow>();
        var children = new HashMap<Integer, List<Integer>>();
        var queue = new ArrayDeque<Integer>();
        for (var row : rows) {
            byRow.put(row.rowNumber(), row);
            Integer parent = dependencies.get(row.rowNumber());
            if (parent == null) queue.add(row.rowNumber());
            else children.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(row.rowNumber());
        }
        var order = new ArrayList<Integer>();
        while (!queue.isEmpty()) {
            int current = queue.remove(); order.add(current);
            Integer parent = dependencies.get(current);
            if (parent != null && !byRow.get(parent).errors().isEmpty())
                byRow.get(current).errors().add("ردیف شغل بالادست دارای خطا است؛ ابتدا خطاهای آن را اصلاح کنید.");
            queue.addAll(children.getOrDefault(current, List.of()));
        }
        var visited = new HashSet<>(order);
        rows.stream().filter(row -> !visited.contains(row.rowNumber()))
            .forEach(row -> row.errors().add("در زنجیرهٔ شغل بالادست چرخه وجود دارد؛ شغل نمی‌تواند بالادست خودش یا یکی از اجدادش باشد."));
        int invalid = (int) rows.stream().filter(row -> !row.errors().isEmpty()).count();
        var immutableRows = rows.stream().map(row -> new ImportRow(row.rowNumber(), row.job(), row.company(), row.parentCode(), List.copyOf(row.errors()))).toList();
        return new Inspection(new ImportReport(immutableRows, rows.size() - invalid, invalid, 0,
            invalid == 0 ? "اطلاعات آمادهٔ ثبت است. پیش‌نمایش را بررسی و ثبت را تأیید کنید." :
                "خطاهای فایل را اصلاح و دوباره بررسی کنید؛ هیچ ردیفی ذخیره نشده است."), List.copyOf(order));
    }

    private static Map<Integer, String> columns(Row header, DataFormatter formatter) {
        if (header == null || header.getLastCellNum() > 10) throw new CatalogException(400, "عنوان ستون‌ها در ردیف اول و مطابق قالب نمونه باشد.");
        var columns = new LinkedHashMap<Integer, String>();
        for (Cell cell : header) {
            String title = formatter.formatCellValue(cell).strip();
            if (title.isBlank()) continue;
            if (cell.getCellType() != CellType.STRING) throw new CatalogException(400, "عنوان ستون باید متن ساده باشد.");
            String field = switch (textKey(title).replaceAll("\\s+", "")) {
                case "کدشغل", "code" -> "code";
                case "عنوانشغل", "عنوان", "title" -> "title";
                case "شرکت", "کدشرکت", "نامشرکت", "company", "companycode", "companytitle" -> "company";
                case "کدشغلبالادست", "کدبالادست", "parentcode", "parent" -> "parentCode";
                case "توضیحات", "description" -> "description";
                default -> throw new CatalogException(400, "عنوان ستون " + (cell.getColumnIndex() + 1) + " شناخته نشد. از قالب نمونه استفاده کنید.");
            };
            if (columns.containsValue(field)) throw new CatalogException(400, "ستون «" + LABELS.get(field) + "» بیش از یک بار آمده است.");
            columns.put(cell.getColumnIndex(), field);
        }
        if (!columns.values().containsAll(List.of("code", "title", "company")))
            throw new CatalogException(400, "ستون‌های کد شغل، عنوان شغل و شرکت الزامی‌اند.");
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
        if (cell.getCellType() == CellType.NUMERIC && (DateUtil.isCellDateFormatted(cell) ||
            Set.of("code", "parentCode", "company").contains(field) &&
                (cell.getNumericCellValue() != Math.rint(cell.getNumericCellValue()) || Math.abs(cell.getNumericCellValue()) > 999999999999999d))) {
            errors.add(LABELS.get(field) + ": کد را به صورت متن با قالب Text وارد کنید؛ تاریخ، اعشار و عدد بیش از ۱۵ رقم مجاز نیست.");
            return null;
        }
        String result = formatter.formatCellValue(cell).strip();
        return result.isEmpty() ? null : result;
    }
    private static String codeKey(String value) { return value.strip().toLowerCase(Locale.ROOT); }
    private static String textKey(String value) {
        return value.replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک').replace('\u200c', ' ')
            .replace('\u00a0', ' ').strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
    private Map<String, Set<Long>> companyOptions() {
        var options = new HashMap<String, Set<Long>>();
        for (var row : repository.list(Catalog.COMPANIES)) {
            long id = ((Number) row.get("id")).longValue();
            String code = row.get("code").toString(), title = row.get("title").toString();
            for (String key : List.of(code, title, code + " — " + title, code + " - " + title))
                options.computeIfAbsent(textKey(key), ignored -> new HashSet<>()).add(id);
        }
        return options;
    }
    private static Long company(String value, Map<String, Set<Long>> options, List<String> errors) {
        if (value == null) return null;
        var matches = options.getOrDefault(textKey(value), Set.of());
        if (matches.size() == 1) return matches.iterator().next();
        errors.add(matches.isEmpty() ? "شرکت: در اطلاعات پایه پیدا نشد؛ ابتدا شرکت را تعریف کنید." :
            "شرکت: مقدار مبهم است؛ از گزینهٔ کامل «کد — عنوان» استفاده کنید.");
        return null;
    }
}
