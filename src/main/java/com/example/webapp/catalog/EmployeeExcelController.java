package com.example.webapp.catalog;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/employees/import")
public class EmployeeExcelController {
    private final EmployeeExcelService service;
    public EmployeeExcelController(EmployeeExcelService service) { this.service = service; }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EmployeeExcelService.ImportReport preview(@RequestParam("file") MultipartFile file) {
        return service.preview(file);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EmployeeExcelService.ImportReport> importEmployees(@RequestParam("file") MultipartFile file) {
        var report = service.importEmployees(file);
        return ResponseEntity.status(report.errorCount() > 0 ? 422 : 201).body(report);
    }
}
