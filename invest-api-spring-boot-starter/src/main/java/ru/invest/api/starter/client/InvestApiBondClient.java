package ru.invest.api.starter.client;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.invest.api.dto.bond.BondDto;

import java.util.List;

public interface InvestApiBondClient {
    @GetMapping("/bonds/foreign/all")
    List<BondDto> getAllForeignBonds(@RequestParam(required = false) Integer batchLimit);
}
