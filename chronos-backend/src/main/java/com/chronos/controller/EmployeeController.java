package com.chronos.controller;

import com.chronos.repository.EmployeeRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EmployeeController {

    private final EmployeeRepository employeeRepository;

    // Spring automatically connects this to your database repository
    public EmployeeController(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    // This manually creates the API endpoint that was missing!
    @GetMapping("/api/employees")
    public Object getAllEmployees() {
        return employeeRepository.findAll();
    }
}