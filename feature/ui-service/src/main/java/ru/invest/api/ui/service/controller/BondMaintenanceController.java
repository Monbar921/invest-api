package ru.invest.api.ui.service.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("/internal/rest/maintenance")
public class BondMaintenanceController {
    @PostMapping("/sync-all")
    public void syncAll(){

    }
}
