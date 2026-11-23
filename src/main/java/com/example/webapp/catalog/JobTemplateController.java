package com.example.webapp.catalog;

import org.apache.poi.ss.usermodel.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.io.*;
import java.util.*;

@RestController
public class JobTemplateController {
    private final CatalogRepository repository;
    public JobTemplateController(CatalogRepository repository) { this.repository = repository; }

    @GetMapping("/templates/jobs-import.xlsx")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> template() throws IOException {
        try (var input = new ClassPathResource("static/templates/jobs-import.xlsx").getInputStream();
             var workbook = WorkbookFactory.create(input); var output = new ByteArrayOutputStream()) {
            var companies = repository.list(Catalog.COMPANIES).reversed();
            var jobs = repository.list(Catalog.JOBS).reversed();
            var companyLabels = new HashMap<Long, String>();
            var jobCodes = new HashMap<Long, String>();
            for (var job : jobs) jobCodes.put(((Number) job.get("id")).longValue(), job.get("code").toString());
            int index = 1;
            for (var company : companies) {
                String label = company.get("code") + " — " + company.get("title");
                companyLabels.put(((Number) company.get("id")).longValue(), label);
                writeRow(workbook.getSheet("شرکت‌ها"), index++, company.get("code"), company.get("title"), label);
            }
            workbook.getName("JobCompanies").setRefersToFormula("'شرکت‌ها'!$C$2:$C$" + Math.max(2, index));
            index = 1;
            for (var job : jobs) {
                String company = job.get("companyId") == null ? "شرکت مشخص نشده" : companyLabels.get(((Number) job.get("companyId")).longValue());
                String parent = job.get("parentId") == null ? "" : jobCodes.get(((Number) job.get("parentId")).longValue());
                writeRow(workbook.getSheet("مشاغل موجود"), index++, job.get("code"), job.get("title"), company, parent);
            }
            workbook.write(output);
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=jobs-import.xlsx")
                .cacheControl(CacheControl.noStore()).body(output.toByteArray());
        }
    }

    private static void writeRow(Sheet sheet, int index, Object... values) {
        var example = sheet.getRow(1);
        var styles = new ArrayList<CellStyle>();
        for (int i = 0; i < values.length; i++) styles.add(example.getCell(i).getCellStyle());
        var row = sheet.getRow(index);
        if (row == null) row = sheet.createRow(index);
        row.setHeightInPoints(36);
        for (int i = 0; i < values.length; i++) {
            var cell = row.createCell(i);cell.setCellStyle(styles.get(i));
            cell.setCellValue(Objects.toString(values[i], ""));
        }
    }
}
