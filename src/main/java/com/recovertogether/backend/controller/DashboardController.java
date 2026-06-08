package com.recovertogether.backend.controller;

import com.recovertogether.backend.dto.DashboardResponse;
import com.recovertogether.backend.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
public class DashboardController
{
    private final DashboardService dashboardService;

    public DashboardController(
            DashboardService dashboardService)
    {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public DashboardResponse getDashboard()
    {
        return dashboardService.getDashboard();
    }
}