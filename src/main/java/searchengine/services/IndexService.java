package searchengine.services;

import searchengine.model.SiteData;

import java.util.Set;

public interface IndexService {
    void startIndexing();
    void stopIndexing();
    String indexPage(String url);
    Set<SiteData> getSitesInIndexing();
}
