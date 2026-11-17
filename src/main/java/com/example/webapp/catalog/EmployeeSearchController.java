package com.example.webapp.catalog;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
public class EmployeeSearchController {
    private final EmployeeSearchService service;
    public EmployeeSearchController(EmployeeSearchService service) { this.service = service; }

    @GetMapping("/api/employees/search")
    public EmployeeSearchService.Result search(@Valid @ModelAttribute EmployeeSearchRequest request) {
        return service.search(request);
    }
}
