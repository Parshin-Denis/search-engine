package searchengine.dto.search;

import lombok.Data;

@Data
public class PageInfoItem {
    private String site;
    private String siteName;
    private String uri;
    private String title;
    private String snippet;
    private float relevance;
}
