package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.LemmaFinder;
import searchengine.model.*;
import searchengine.SiteResearcher;
import searchengine.config.Account;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class IndexServiceImpl implements IndexService {
    private final SitesList sites;
    private final Account account;
    private final Map<SiteData, List<PageData>> sitesIndexing = new HashMap<>();
    @Autowired
    @Getter
    private PageRepository pageRepository;
    @Autowired
    private SiteRepository siteRepository;
    @Autowired
    private LemmaRepository lemmaRepository;
    @Autowired
    private IndexRepository indexRepository;

    @Override
    public void startIndexing() {
        ForkJoinPool pool = new ForkJoinPool();
        for (Site site : sites.getSites()) {
            new Thread(() -> {
                SiteData siteData = siteRepository.findFirstByName(site.getName());
                if (siteData != null) {
                    sitesIndexing.put(siteData, null);
                    deleteSiteData(siteData);
                    siteData.setStatusTime(LocalDateTime.now());
                    siteData.setUrl(getSiteUrl(site));
                } else {
                    siteData = new SiteData(SiteStatus.INDEXING, LocalDateTime.now(),
                            "", getSiteUrl(site), site.getName());
                }
                siteRepository.save(siteData);

                List<PageData> pageDataList = new ArrayList<>();
                pageDataList.add(new PageData(siteData, "/", 0, ""));
                sitesIndexing.put(siteData, pageDataList);

                pool.invoke(new SiteResearcher(pageDataList.get(0), pageDataList, this));

                if (siteData.getStatus() == SiteStatus.INDEXING) {
                    insertAllData(pageDataList, siteData);
                    siteData.setStatus(SiteStatus.INDEXED);
                    siteRepository.save(siteData);
                }
                sitesIndexing.remove(siteData);
            }).start();
        }
    }

    @Override
    public void stopIndexing() {
        for (SiteData siteData : sitesIndexing.keySet()) {
            siteData.setStatus(SiteStatus.FAILED);
            siteData.setLastError("Индексация остановлена пользователем");
            siteRepository.save(siteData);
        }
    }

    @Override
    public String indexPage(String url) {
        SiteData siteData = findSiteData(url);
        if (siteData == null) {
            return "Данная страница находится за пределами сайтов, \n" +
                    "указанных в конфигурационном файле\n";
        }

        Document doc;
        try {
            doc = getDocument(url);
        } catch (IOException e) {
            return "Страница не доступна";
        }

        PageData pageData = pageRepository
                .findFirstByPathAndSite(getRelativeUrl(doc.location(), siteData.getUrl()), siteData);
        if (pageData != null) {
            deletePageData(pageData);
        }

        pageData = new PageData(siteData, getRelativeUrl(doc.location(), siteData.getUrl()),
                doc.connection().response().statusCode(), doc.html());

        List<PageData> pageDataList = new ArrayList<>();
        pageDataList.add(pageData);
        insertAllData(pageDataList, siteData);

        return "OK";
    }

    @Override
    public Set<SiteData> getSitesInIndexing() {
        return sitesIndexing.keySet();
    }

    public void deleteSiteData(SiteData siteData) {
        siteData.setStatus(SiteStatus.INDEXING);
        siteRepository.save(siteData);
        while (pageRepository.countBySite(siteData) != 0 && siteData.getStatus() != SiteStatus.FAILED) {
            List<PageData> pageDataList = pageRepository.findFirst500BySite(siteData);
            pageRepository.deleteAll(pageDataList);
        }
        if (siteData.getStatus() != SiteStatus.FAILED) {
            lemmaRepository.deleteAll(lemmaRepository.findAllBySite(siteData));
        }
    }

    public void deletePageData(PageData pageData) {
        List<LemmaData> lemmaDataList = lemmaRepository.findAllByPage(pageData.getId());
        List<LemmaData> lemmaDataListToUpdate = new ArrayList<>();
        List<LemmaData> lemmaDataListToDelete = new ArrayList<>();

        lemmaDataList.forEach(lemmaData -> {
            if (lemmaData.getFrequency() > 1) {
                lemmaData.setFrequency(lemmaData.getFrequency() - 1);
                lemmaDataListToUpdate.add(lemmaData);
            } else {
                lemmaDataListToDelete.add(lemmaData);
            }
        });

        pageRepository.delete(pageData);
        lemmaRepository.deleteAll(lemmaDataListToDelete);
        lemmaRepository.saveAll(lemmaDataListToUpdate);
    }

    public void insertAllData(List<PageData> pageDataList, SiteData siteData) {
        List<LemmaData> lemmasToInsert = new ArrayList<>();
        List<IndexData> indexesToInsert = new ArrayList<>();
        List<LemmaData> lemmaDataList = lemmaRepository.findAllBySite(siteData);
        for (PageData pageData : pageDataList) {
            if (pageData.getCode() >= 400) {
                continue;
            }
            HashMap<String, Integer> lemmasMap = LemmaFinder.getLemmaMap(pageData.getContent());
            for (String lemma : lemmasMap.keySet()) {
                boolean isLemmaInListToInsert = true;
                LemmaData lemmaData = findLemmaInList(lemmasToInsert, lemma);
                if (lemmaData == null) {
                    lemmaData = findLemmaInList(lemmaDataList, lemma);
                    isLemmaInListToInsert = false;
                }

                if (lemmaData == null) {
                    lemmaData = new LemmaData(siteData, lemma, 1);
                } else {
                    lemmaData.setFrequency(lemmaData.getFrequency() + 1);
                }

                if (!isLemmaInListToInsert) {
                    lemmasToInsert.add(lemmaData);
                }
                indexesToInsert.add(new IndexData(pageData, lemmaData, lemmasMap.get(lemma)));
            }
        }
        pageRepository.saveAll(pageDataList);
        lemmaRepository.saveAll(lemmasToInsert);
        indexRepository.saveAll(indexesToInsert);
        siteData.setStatusTime(LocalDateTime.now());
        siteRepository.save(siteData);
    }

    public String getRelativeUrl(String url, String siteUrl) {
        String urlWoWWW = url.replaceFirst("//www.", "//");
        String siteUrlWoWWW = siteUrl.replaceFirst("//www.", "//");
        if (!urlWoWWW.startsWith(siteUrlWoWWW)) {
            return "";
        }

        String relativeUrl = urlWoWWW.substring(siteUrlWoWWW.length());
        if (!relativeUrl.startsWith("/")) {
            relativeUrl = "/".concat(relativeUrl);
        }

        return relativeUrl;
    }

    public String getSiteUrl(Site site) {
        try {
            Document doc = getDocument(site.getUrl());
            return doc.location().endsWith("/")
                    ? doc.location().substring(0, doc.location().length() - 1)
                    : doc.location();
        } catch (IOException e) {
            return site.getUrl();
        }
    }

    public SiteData findSiteData(String url) {
        List<SiteData> siteDataList = siteRepository.findAll();
        SiteData siteData = siteDataList
                .stream()
                .filter(s -> !getRelativeUrl(url, s.getUrl()).isBlank())
                .findFirst()
                .orElse(null);
        if (siteData == null) {
            Site site = sites.getSites()
                    .stream()
                    .filter(s -> !getRelativeUrl(url, s.getUrl()).isBlank())
                    .findFirst()
                    .orElse(null);
            if (site != null) {
                siteData = new SiteData(SiteStatus.INDEXING, LocalDateTime.now(),
                        "", getSiteUrl(site), site.getName());
                siteRepository.save(siteData);
            }
        }
        return siteData;
    }

    public LemmaData findLemmaInList(List<LemmaData> lemmaDataList, String lemma) {
        return lemmaDataList
                .stream()
                .filter(l -> l.getLemma().equals(lemma))
                .findFirst()
                .orElse(null);
    }

    public Document getDocument(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent(account.getUserAgent())
                .referrer(account.getReferrer())
                .get();
    }
}
