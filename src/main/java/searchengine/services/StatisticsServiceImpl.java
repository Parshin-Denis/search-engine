package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.model.SiteData;
import searchengine.model.SiteStatus;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final Random random = new Random();
    private final SitesList sites;
    @Autowired
    private PageRepository pageRepository;
    @Autowired
    private SiteRepository siteRepository;
    @Autowired
    private LemmaRepository lemmaRepository;


    @Override
    public StatisticsResponse getStatistics() {
        TotalStatistics total = new TotalStatistics();
        total.setSites((int) siteRepository.count());
        total.setIndexing(siteRepository.existsByStatus(SiteStatus.INDEXING));

        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<SiteData> siteDataList = siteRepository.findAll();
        for (SiteData siteData: siteDataList) {
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(siteData.getName());
            item.setUrl(siteData.getUrl());
            int pages = pageRepository.countBySite(siteData);
            int lemmas = lemmaRepository.countBySite(siteData);
            item.setPages(pages);
            item.setLemmas(lemmas);
            item.setStatus(siteData.getStatus().toString());
            item.setError(siteData.getLastError());
            item.setStatusTime(siteData.getStatusTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            total.setPages(total.getPages() + pages);
            total.setLemmas(total.getLemmas() + lemmas);
            detailed.add(item);
        }

        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }
}
