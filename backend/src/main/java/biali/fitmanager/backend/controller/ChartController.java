package biali.fitmanager.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import biali.fitmanager.backend.dto.ChartDataResponse;
import biali.fitmanager.backend.service.ChartService;

@RestController
@RequestMapping("/api/admin/charts")
public class ChartController {

    private final ChartService chartService;

    public ChartController(ChartService chartService) {
        this.chartService = chartService;
    }

    @GetMapping("/data")
    public ResponseEntity<ChartDataResponse> getChartData() {
        ChartDataResponse data = chartService.getChartData();
        return ResponseEntity.ok(data);
    }
}
