package ru.invest.api.stock.supplier.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/rest/ticker/share")
@RequiredArgsConstructor
public class ShareTickerController {
    @GetMapping("/{code}")
    public void getPrice(final @PathVariable String code) {
    }
}
