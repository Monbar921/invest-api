package ru.invest.api.ui.service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.invest.api.dto.portfolio.imports.BrokerAccountDto;
import ru.invest.api.dto.portfolio.imports.BrokerConnectionDto;
import ru.invest.api.dto.portfolio.imports.BrokerTokenRequest;
import ru.invest.api.dto.portfolio.imports.ImportApplyRequest;
import ru.invest.api.dto.portfolio.imports.ImportSourceDto;
import ru.invest.api.dto.portfolio.imports.PortfolioImportDto;
import ru.invest.api.dto.portfolio.imports.TinvestImportRequest;
import ru.invest.api.imports.model.ImportSource;
import ru.invest.api.imports.usecase.BrokerConnectionUseCase;
import ru.invest.api.imports.usecase.PortfolioImportUseCase;
import ru.invest.api.ui.service.mapper.ImportDtoMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Импорт сделок портфеля от брокера: токен T-Invest только на чтение, файлы отчётов, предпросмотр и подтверждение.
 * Пользователь - X-User-Id от gateway, без него 401. Наружу через gateway: /api/portfolio/imports/**.
 */
@Validated
@RestController
@RequestMapping("/internal/rest/portfolio/imports")
@RequiredArgsConstructor
public class PortfolioImportController {
    private final BrokerConnectionUseCase brokerConnectionUseCase;
    private final PortfolioImportUseCase portfolioImportUseCase;
    private final ImportDtoMapper importDtoMapper;

    /**
     * Подключён ли токен T-Invest и включён ли импорт по токену. Сам токен не отдаётся - только последние 4 символа.
     */
    @GetMapping("/tinvest")
    public BrokerConnectionDto getConnection(
            @RequestHeader(value = UserIdHeader.NAME, required = false) final String userId) {
        return importDtoMapper.toDto(brokerConnectionUseCase.get(UserIdHeader.require(userId)));
    }

    /**
     * Подключить токен (прежний заменяется): проверяется в T-Invest, что он только на чтение, и сохраняется
     * зашифрованным. Импорта нет в тарифе - 402; токен не подошёл, с правами на торговлю, без счетов или импорт по
     * токену выключен - 422; T-Invest не ответил - 502.
     */
    @PutMapping("/tinvest")
    public BrokerConnectionDto connect(@RequestHeader(value = UserIdHeader.NAME, required = false) final String userId,
                                       @RequestBody final BrokerTokenRequest request) {
        return importDtoMapper.toDto(brokerConnectionUseCase.connect(UserIdHeader.require(userId),
                request.getToken()));
    }

    /** Удалить токен; если его не было - тоже 204 */
    @DeleteMapping("/tinvest")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(@RequestHeader(value = UserIdHeader.NAME, required = false) final String userId) {
        brokerConnectionUseCase.disconnect(UserIdHeader.require(userId));
    }

    /** Счета подключённого токена; токен не подключён - 422 */
    @GetMapping("/tinvest/accounts")
    public List<BrokerAccountDto> getAccounts(
            @RequestHeader(value = UserIdHeader.NAME, required = false) final String userId) {
        return importDtoMapper.toAccountDto(brokerConnectionUseCase.getAccounts(UserIdHeader.require(userId)));
    }

    /**
     * Предпросмотр сделок со счетов T-Invest: ничего не меняет в портфеле. 201 - создан импорт со статусом PREVIEW.
     */
    @PostMapping("/tinvest/preview")
    @ResponseStatus(HttpStatus.CREATED)
    public PortfolioImportDto previewTinvest(
            @RequestHeader(value = UserIdHeader.NAME, required = false) final String userId,
            @RequestBody(required = false) final TinvestImportRequest request) {
        final TinvestImportRequest body = request == null ? new TinvestImportRequest() : request;
        return importDtoMapper.toDto(portfolioImportUseCase.previewTinvest(UserIdHeader.require(userId),
                body.getAccountIds(), body.getFrom(), body.getBroker()));
    }

    /**
     * Предпросмотр сделок из файла отчёта (multipart, поле file). format - CSV, SBER_HTML или ALFA_XLSX; broker -
     * брокер у созданных сделок (пусто - из отчёта); файл не этого формата, пустой или слишком большой - 422.
     */
    @PostMapping(value = "/file/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PortfolioImportDto previewFile(
            @RequestHeader(value = UserIdHeader.NAME, required = false) final String userId,
            @RequestParam final ImportSourceDto format,
            @RequestParam(required = false) final String broker,
            @RequestPart("file") final MultipartFile file) {
        final String user = UserIdHeader.require(userId);
        try {
            return importDtoMapper.toDto(portfolioImportUseCase.previewFile(user, ImportSource.valueOf(format.name()),
                    file.getOriginalFilename(), file.getBytes(), broker));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Форматы файлов отчётов, которые умеет разбирать импорт */
    @GetMapping("/file/formats")
    public List<ImportSourceDto> getFileFormats() {
        return portfolioImportUseCase.fileFormats().stream()
                .map(source -> ImportSourceDto.valueOf(source.name()))
                .toList();
    }

    /** Последние 20 импортов пользователя, без строк */
    @GetMapping
    public List<PortfolioImportDto> list(@RequestHeader(value = UserIdHeader.NAME, required = false) final String userId) {
        return importDtoMapper.toDto(portfolioImportUseCase.list(UserIdHeader.require(userId)));
    }

    /** Предпросмотр или отчёт импорта со строками; чужой - 404 */
    @GetMapping("/{id}")
    public PortfolioImportDto get(@RequestHeader(value = UserIdHeader.NAME, required = false) final String userId,
                                  @PathVariable final Long id) {
        return importDtoMapper.toDto(portfolioImportUseCase.get(UserIdHeader.require(userId), id));
    }

    /**
     * Подтвердить импорт: создать выбранные сделки. Отчёт - в ответе (CREATED, FAILED с причиной, SKIPPED, DUPLICATE).
     * Уже подтверждён - 422.
     */
    @PostMapping("/{id}/apply")
    public PortfolioImportDto apply(@RequestHeader(value = UserIdHeader.NAME, required = false) final String userId,
                                    @PathVariable final Long id,
                                    @RequestBody @Valid final ImportApplyRequest request) {
        return importDtoMapper.toDto(portfolioImportUseCase.apply(UserIdHeader.require(userId), id,
                request.getItemIds()));
    }
}
