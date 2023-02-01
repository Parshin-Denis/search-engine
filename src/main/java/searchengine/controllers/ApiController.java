package searchengine.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.ErrorResponse;
import searchengine.dto.OkResponse;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexService;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;

import java.net.URI;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexService indexService;
    private final SearchService searchService;

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
        String result = indexService.startIndexing();
        if (result.equals("OK")) {
            return ResponseEntity.ok(new OkResponse());
        }
        return ResponseEntity.ok(new ErrorResponse(result));
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity stopIndexing() {
        if (indexService.stopIndexing()) {
            return ResponseEntity.ok(new OkResponse());
        }
        return ResponseEntity.ok(new ErrorResponse("Индексация не запущена"));
    }

    @PostMapping("/indexPage")
    public ResponseEntity indexPage(@RequestParam String url) {
        String result = indexService.indexPage(url);
        if (result.equals("OK")) {
            return ResponseEntity.ok(new OkResponse());
        }
        return ResponseEntity.ok(new ErrorResponse(result));
    }

    @GetMapping("/search")
    public ResponseEntity search (@RequestParam String query, String site, int offset, int limit ) {
        if(query.isBlank()) {
            return ResponseEntity.ok(new ErrorResponse("Задан пустой поисковый запрос"));
        }
        SearchResponse response = searchService.searchPages(query, site, offset, limit);
        if (!response.isResult()) {
            return ResponseEntity.ok(new ErrorResponse("Сайт не проиндексирован"));
        }
        return ResponseEntity.ok(response);
    }
}
