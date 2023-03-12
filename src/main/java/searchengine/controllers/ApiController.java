package searchengine.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.ErrorResponse;
import searchengine.dto.OkResponse;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.model.PageData;
import searchengine.model.SiteData;
import searchengine.model.SiteStatus;
import searchengine.services.IndexService;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexService indexService;
    private final SearchService searchService;
    private String queryInProcess = "";
    private String pageIndexing = "";


    public ApiController(StatisticsService statisticsService, IndexService indexService, SearchService searchService) {
        this.statisticsService = statisticsService;
        this.indexService = indexService;
        this.searchService = searchService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity startIndexing() {
        if (!pageIndexing.isBlank()){
            return ResponseEntity.ok(new ErrorResponse("Индексация невозможна. Запущена индексация страницы"));
        }
        if(!queryInProcess.isBlank()) {
            return ResponseEntity.ok(new ErrorResponse("Индексация невозможна. Выполняется поисковый запрос."));
        }
        if (!indexService.getSitesInIndexing().isEmpty()) {
            if (indexService.getSitesInIndexing().stream().anyMatch(s -> s.getStatus() == SiteStatus.INDEXING)){
                return ResponseEntity.ok(new ErrorResponse("Индексация уже запущена"));
            }
            return ResponseEntity.ok(new ErrorResponse("Предыдущая индексация еще не завершена"));
        }

        indexService.startIndexing();

        return ResponseEntity.ok(new OkResponse());
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity stopIndexing() {
        if (indexService.getSitesInIndexing().isEmpty()) {
            return ResponseEntity.ok(new ErrorResponse("Индексация не запущена"));
        }
        indexService.stopIndexing();
        return ResponseEntity.ok(new OkResponse());
    }

    @PostMapping("/indexPage")
    public ResponseEntity indexPage(@RequestParam String url) {
        if(!indexService.getSitesInIndexing().isEmpty()){
            return ResponseEntity.ok(new ErrorResponse("Индексация невозможна. Выполняется индексация сайтов."));
        }
        if(!queryInProcess.isBlank()) {
            return ResponseEntity.ok(new ErrorResponse("Индексация невозможна. Выполняется поисковый запрос."));
        }
        String regexSiteUrl= "^https?://.+";
        if (!url.matches(regexSiteUrl)){
            return ResponseEntity.ok(new ErrorResponse("Введён некорректный адрес"));
        }
        if (pageIndexing.contains(url)) {
            return ResponseEntity.ok(new ErrorResponse("Эта страница уже индексируется"));
        }

        pageIndexing = url;
        String result = indexService.indexPage(url);
        pageIndexing = "";

        if (result.equals("OK")) {
            return ResponseEntity.ok(new OkResponse());
        }
        return ResponseEntity.ok(new ErrorResponse(result));
    }

    @GetMapping("/search")
    public ResponseEntity search (@RequestParam String query, String site, int offset, int limit ) {
        if(!indexService.getSitesInIndexing().isEmpty()){
            return ResponseEntity.ok(new ErrorResponse("Поиск невозможен. Выполняется индексация сайтов."));
        }
        if (!pageIndexing.isBlank()){
            return ResponseEntity.ok(new ErrorResponse("Поиск невозможен. Запущена индексация страницы"));
        }
        if(query.isBlank()) {
            return ResponseEntity.ok(new ErrorResponse("Задан пустой поисковый запрос"));
        }
        if(!queryInProcess.isBlank()) {
            return ResponseEntity.ok(new ErrorResponse("Обрабатывается запрос \"" + queryInProcess + "\""));
        }
        queryInProcess = query;
        SearchResponse response = searchService.searchPages(query, site, offset, limit);
        queryInProcess = "";
        if (!response.isResult()) {
            return ResponseEntity.ok(new ErrorResponse("Сайт(ы) не проиндексирован(ы)"));
        }
        return ResponseEntity.ok(response);
    }
}
