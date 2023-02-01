package searchengine.services;

public interface IndexService {
    String startIndexing();
    boolean stopIndexing();
    String indexPage(String url);
}
