package com.example.webapp.catalog;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.io.*;

/** Include current reference choices in each downloaded workbook. */
@RestController
public class EmployeeTemplateController {
    private final CatalogRepository repository;
    public EmployeeTemplateController(CatalogRepository repository) { this.repository = repository; }

    @GetMapping("/templates/employees-import.xlsx")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> template() throws IOException {
        try (var input = new ClassPathResource("static/templates/employees-import.xlsx").getInputStream();
             var workbook = WorkbookFactory.create(input); var output = new ByteArrayOutputStream()) {
            choices(workbook, Catalog.SPECIALITIES, "Specialities", 1, 10);
            choices(workbook, Catalog.ORGANIZATIONAL_LEVELS, "OrganizationalLevels", 2, 12);
            workbook.write(output);
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=employees-import.xlsx")
                .cacheControl(CacheControl.noStore()).body(output.toByteArray());
        }
    }
    private void choices(Workbook workbook, Catalog catalog, String rangeName, int column, int targetColumn) {
        var rows = repository.list(catalog);
        var options = workbook.getSheet("گزینه‌ها");
        int index = 1;
        for (var value : rows.reversed()) {
            Row row = options.getRow(index);
            if (row == null) row = options.createRow(index);
            row.createCell(column).setCellValue(value.get("code") + " — " + value.get("title"));
            index++;
        }
        var range = workbook.createName();range.setNameName(rangeName);
        String letter = org.apache.poi.ss.util.CellReference.convertNumToColString(column);
        range.setRefersToFormula("'گزینه‌ها'!$" + letter + "$2:$" + letter + "$" + Math.max(2, index));
        var sheet = workbook.getSheetAt(0);var helper = sheet.getDataValidationHelper();
        var validation = helper.createValidation(helper.createFormulaListConstraint(rangeName), new CellRangeAddressList(1, 1000, targetColumn, targetColumn));
        validation.setEmptyCellAllowed(true);validation.setShowErrorBox(true);validation.setShowPromptBox(true);
        validation.createErrorBox("گزینه نامعتبر", "گزینه‌ای از اطلاعات پایه انتخاب کنید؛ در صورت نیاز قالب تازه دریافت کنید.");
        validation.createPromptBox("اطلاعات پایه", "کد یا عنوان تعریف‌شده در سامانه را وارد کنید یا از فهرست انتخاب کنید.");
        sheet.addValidationData(validation);
    }
}
