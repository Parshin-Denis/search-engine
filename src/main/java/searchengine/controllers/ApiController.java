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
        return indexService.startIndexing();
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity stopIndexing() {
        return indexService.stopIndexing();
    }

    @PostMapping("/indexPage")
    public ResponseEntity indexPage(@RequestParam String url) {
        return indexService.indexPage(url);
    }

    @GetMapping("/search")
    public ResponseEntity search (@RequestParam String query, String site, int offset, int limit ) {
        return searchService.searchPages(query, site, offset, limit);
    }
}
