package searchengine.services;

import org.springframework.http.ResponseEntity;

public interface IndexService {
    ResponseEntity startIndexing();
    ResponseEntity stopIndexing();
    ResponseEntity indexPage(String url);
}
