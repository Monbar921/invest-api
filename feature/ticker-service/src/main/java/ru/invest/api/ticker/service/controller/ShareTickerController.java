package ru.invest.api.ticker.service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.invest.api.ticker.service.usecase.ShareUseCase;

import java.math.BigDecimal;

@RestController
@RequestMapping("/internal/rest/ticker/share")
@RequiredArgsConstructor
public class ShareTickerController {
    private final ShareUseCase shareUseCase;

    @GetMapping
    public void getPrice(){
        final BigDecimal price = shareUseCase.getSharePrice("SBER", "TQBR");
        System.out.println(price.toString());
    }
}
