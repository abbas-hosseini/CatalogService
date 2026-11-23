package com.example.webapp.catalog;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/jobs/import")
public class JobExcelController {
    private final JobExcelService service;
    public JobExcelController(JobExcelService service) { this.service = service; }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public JobExcelService.ImportReport preview(@RequestParam("file") MultipartFile file) {
        return service.preview(file);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<JobExcelService.ImportReport> importJobs(@RequestParam("file") MultipartFile file) {
        var report = service.importJobs(file);
        return ResponseEntity.status(report.errorCount() > 0 ? 422 : 201).body(report);
    }
}
