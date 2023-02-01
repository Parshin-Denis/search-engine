package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import searchengine.Model.PageData;
import searchengine.Model.SiteData;
import searchengine.Model.SiteStatus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveTask;

public class SiteResearcher extends RecursiveTask<Integer> {
    private String url;
    private SiteData siteData;
    private IndexServiceImpl indexService;

    public SiteResearcher(String url, SiteData siteData,
                          IndexServiceImpl indexService) {
        this.url = url;
        this.indexService = indexService;
        this.siteData = siteData;
    }

    @Override
    protected Integer compute() {
        if (siteData.getStatus() == SiteStatus.FAILED) {
            return 0;
        }

        Integer urlNumber = 1;
        List<SiteResearcher> siteResearcherList = new ArrayList<>();

        Document doc = null;
        try {
            doc = Jsoup.connect(url)
                    .userAgent(indexService.getAccount().getUserAgent())
                    .referrer(indexService.getAccount().getReferrer())
                    .get();
        } catch (IOException e) {
            return urlNumber;
        }

        PageData pageData = new PageData(siteData, indexService.getRelativeUrl(doc.location()),
                doc.connection().response().statusCode(), doc.html());

        if (!indexService.insertPageData(pageData, siteData)) {
            return 0;
        }

        if (pageData.getCode() < 400) {
            indexService.insertLemmaAndIndexData(pageData, siteData);
        }

        Elements elements = doc.select("a[href~=^[^#]+$]");
        elements.forEach(element -> {
            String urlChild = element.attr("abs:href");
            if (urlChild.indexOf(siteData.getUrl()) == 0 ||
                    urlChild.indexOf(siteData.getUrl().replaceFirst("www.", "")) == 0) {
                SiteResearcher siteResearcher = new SiteResearcher(urlChild, siteData, indexService);
                siteResearcher.fork();
                siteResearcherList.add(siteResearcher);
            }
        });

        for (SiteResearcher siteResearcher : siteResearcherList) {
            urlNumber += siteResearcher.join();
        }
        return urlNumber;
    }
}