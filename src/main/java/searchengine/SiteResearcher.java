package searchengine;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.Model.PageData;
import searchengine.Model.SiteData;
import searchengine.Model.SiteStatus;
import searchengine.services.IndexServiceImpl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.RecursiveTask;

public class SiteResearcher extends RecursiveTask<List<PageData>> {
    private String url;
    private SiteData siteData;
    private IndexServiceImpl indexService;
    private Set<String> foundPages;

    public SiteResearcher(String url, SiteData siteData,
                          IndexServiceImpl indexService, Set<String> foundPages) {
        this.url = url;
        this.indexService = indexService;
        this.siteData = siteData;
        this.foundPages = foundPages;
        if(foundPages.isEmpty()) {
            foundPages.add(indexService.getRelativeUrl(url));
        }
    }

    @Override
    protected List<PageData> compute() {
        List<PageData> pageDataList = new ArrayList<>();

        if (siteData.getStatus() == SiteStatus.FAILED) {
            return pageDataList;
        }

        Document doc = null;
        try {
            doc = Jsoup.connect(url)
                    .userAgent(indexService.getAccount().getUserAgent())
                    .referrer(indexService.getAccount().getReferrer())
                    .get();
        } catch (IOException e) {
            return pageDataList;
        }

        pageDataList.add(new PageData(siteData, indexService.getRelativeUrl(doc.location()),
                doc.connection().response().statusCode(), doc.html()));

        List<SiteResearcher> siteResearcherList = new ArrayList<>();

        Elements elements = doc.select("a[href~=^[^#]+$]");
        for (Element element: elements){
            String urlChild = element.attr("abs:href");

            if (urlChild.indexOf(siteData.getUrl()) != 0 &&
                    urlChild.indexOf(siteData.getUrl().replaceFirst("www.", "")) != 0) {
                continue;
            }

            String relativeUrlChild = indexService.getRelativeUrl(urlChild);
            synchronized (foundPages) {
                if (foundPages.contains(relativeUrlChild)) {
                    continue;
                }
                foundPages.add(relativeUrlChild);
            }

            SiteResearcher siteResearcher = new SiteResearcher(urlChild, siteData, indexService, foundPages);
            siteResearcher.fork();
            siteResearcherList.add(siteResearcher);
        }

        for (SiteResearcher siteResearcher : siteResearcherList) {
            pageDataList.addAll(siteResearcher.join());
        }
        return pageDataList;
    }
}