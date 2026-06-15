package ru.invest.api.starter.client;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import ru.invest.api.dto.bond.BondDto;
import ru.invest.api.dto.request.bond.BondSortRequest;

import java.util.List;

public interface InvestApiBondClient {
    @GetMapping("/bonds/foreign/all")
    List<BondDto> getAllForeignBonds(@RequestParam(required = false) Integer batchLimit,
                                     @RequestBody(required = false) List<@Valid BondSortRequest> bondSortRequest);
}
