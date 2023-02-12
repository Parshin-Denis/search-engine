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
    private Set<String> foundUrls;

    public SiteResearcher(String url, SiteData siteData, IndexServiceImpl indexService) {
        this.url = url;
        this.indexService = indexService;
        this.siteData = siteData;
        this.foundUrls = indexService.getSitesIndexing().get(siteData);
        if (foundUrls.isEmpty()) {
            foundUrls.add(indexService.getRelativeUrl(url));
        }
    }

    @Override
    protected List<PageData> compute() {
        List<PageData> pageDataList = new ArrayList<>();

        if (siteData.getStatus() == SiteStatus.FAILED) {
            return pageDataList;
        }

        Document doc;
        try {
            doc = Jsoup.connect(url)
                    .userAgent(indexService.getAccount().getUserAgent())
                    .referrer(indexService.getAccount().getReferrer())
                    .get();
        } catch (IOException e) {
            return pageDataList;
        }

        String relativeDocUrl = indexService.getRelativeUrl(doc.location());
        String relativeUrlUrl = indexService.getRelativeUrl(url);
        if (!relativeDocUrl.equals(relativeUrlUrl)) {
            synchronized (foundUrls) {
                if (foundUrls.contains(relativeDocUrl)) {
                    return pageDataList;
                }
                foundUrls.add(relativeDocUrl);
            }
        }

        if (relativeDocUrl.length() > PageData.MAX_LENGTH_PATH) {
            return pageDataList;
        }

        pageDataList.add(new PageData(siteData, relativeDocUrl, doc.connection().response().statusCode(), doc.html()));

        List<SiteResearcher> siteResearcherList = getUrlChildResearcherList(doc);

        for (SiteResearcher siteResearcher : siteResearcherList) {
            pageDataList.addAll(siteResearcher.join());
        }

        if (pageDataList.size() > 300) {
            synchronized (siteData) {
                indexService.insertAllData(pageDataList, siteData);
            }
            pageDataList.clear();
        }
        return pageDataList;
    }

    private List<SiteResearcher> getUrlChildResearcherList (Document doc){
        List<SiteResearcher> siteResearcherList = new ArrayList<>();
        Elements elements = doc.select("a[href~=^[^#?]+$]");
        for (Element element : elements) {
            String urlChild = element.attr("abs:href");

            if (urlChild.indexOf(siteData.getUrl()) != 0 &&
                    urlChild.indexOf(siteData.getUrl().replaceFirst("www.", "")) != 0) {
                continue;
            }

            String relativeUrlChild = indexService.getRelativeUrl(urlChild);
            synchronized (foundUrls) {
                if (foundUrls.contains(relativeUrlChild)) {
                    continue;
                }
                foundUrls.add(relativeUrlChild);
            }

            SiteResearcher siteResearcher = new SiteResearcher(urlChild, siteData, indexService);
            siteResearcher.fork();
            siteResearcherList.add(siteResearcher);
        }
        return siteResearcherList;
    }
}