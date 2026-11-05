package ru.invest.api.ui.service.mapper;

import org.mapstruct.Mapper;
import org.springframework.data.domain.Page;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.event.BondEventModel;
import ru.invest.api.common.model.news.MarketOverviewModel;
import ru.invest.api.common.model.news.NewsModel;
import ru.invest.api.common.model.news.NewsSource;
import ru.invest.api.dto.bond.event.BondEventDto;
import ru.invest.api.dto.bond.news.MarketOverviewDto;
import ru.invest.api.dto.bond.news.NewsDto;
import ru.invest.api.dto.bond.news.NewsSourceDto;
import ru.invest.api.dto.page.PageDto;

import java.util.List;
import java.util.Set;

@Mapper(uses = DateTimeMapper.class)
public interface NewsDtoMapper {
    NewsDto toDto(NewsModel news);

    List<NewsDto> toDto(List<NewsModel> news);

    default PageDto<NewsDto> toDto(final Page<NewsModel> page) {
        return new PageDto<NewsDto>()
                .setContent(toDto(page.getContent()))
                .setPage(page.getNumber())
                .setSize(page.getSize())
                .setTotalElements(page.getTotalElements())
                .setTotalPages(page.getTotalPages());
    }

    Set<NewsSource> toSources(Set<NewsSourceDto> sources);

    MarketOverviewDto toOverviewDto(MarketOverviewModel overview);

    List<BondEventDto> toEventDto(List<BondEventModel> events);
}
