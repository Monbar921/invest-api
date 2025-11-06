package ru.invest.api.share.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.invest.api.share.usecase.ShareUseCase;

import java.math.BigDecimal;

@RestController
@RequestMapping("/internal/rest/ticker/share")
@RequiredArgsConstructor
public class ShareTickerController {
    private final ShareUseCase shareUseCase;

    @GetMapping("/{code}")
    public void getPrice(final @PathVariable String code){
        final BigDecimal price = shareUseCase.getCommonSharePrice(code);
        System.out.println(price.toString());
    }
}
