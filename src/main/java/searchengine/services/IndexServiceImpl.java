package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.LemmaFinder;
import searchengine.Model.*;
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
    @Getter
    private Map<SiteData, List<PageData>> sitesIndexing = new HashMap<>();
    private Set<String> pagesIndexing = new HashSet<>();
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
    public String startIndexing() {
        if (!sitesIndexing.isEmpty()) {
            if (siteRepository.existsByStatus(SiteStatus.INDEXING)){
                return "Индексация уже запущена";
            }
            return "Индексация еще выполняется";
        }
        if (!pagesIndexing.isEmpty()){
            return "Запущена индексация страницы";
        }

        ForkJoinPool pool = new ForkJoinPool();
        for (Site site : sites.getSites()) {
            new Thread(() -> {
                SiteData siteData = siteRepository.findFirstByName(site.getName());
                if (siteData != null) {
                    siteRepository.delete(siteData);
                }

                siteData = new SiteData(SiteStatus.INDEXING, LocalDateTime.now(),
                        null, site.getUrl(), site.getName());
                siteRepository.save(siteData);

                List<PageData> pageDataList = new ArrayList<>();
                pageDataList.add(new PageData(siteData, getRelativeUrl(siteData.getUrl()), 0, ""));
                sitesIndexing.put(siteData, pageDataList);

                pool.invoke(new SiteResearcher(pageDataList.get(0), siteData, this));

                insertAllData(pageDataList, siteData);

                sitesIndexing.remove(siteData);
                if (siteData.getStatus() == SiteStatus.INDEXING) {
                    siteData.setStatus(SiteStatus.INDEXED);
                    siteRepository.save(siteData);
                }
            }).start();
        }
        return "OK";
    }

    @Override
    public boolean stopIndexing() {
        if (sitesIndexing.isEmpty()) {
            return false;
        }

        for (SiteData siteData : sitesIndexing.keySet()) {
            siteData.setStatus(SiteStatus.FAILED);
            siteData.setLastError("Индексация остановлена пользователем");
            siteRepository.save(siteData);
        }
        return true;
    }

    @Override
    public String indexPage(String url) {
        String siteUrl = getSiteUrl(url);
        if (siteUrl == null) {
            return "Введён некорректный адрес";
        }

        if (sitesIndexing.keySet().stream().anyMatch(siteData -> siteData.getUrl().equals(siteUrl))){
            return "Индексация невозможна: Данный сайт еще индексируется";
        }

        Document doc = null;
        try {
            doc = Jsoup.connect(url)
                    .userAgent(account.getUserAgent())
                    .referrer(account.getReferrer())
                    .get();
        } catch (IOException e) {
            return "Страница не доступна";
        }

        SiteData siteData = siteRepository.findFirstByUrl(siteUrl);
        if (siteData == null) {
            siteData = findInConfigFile(siteUrl);
            if (siteData == null) {
                return "Данная страница находится за пределами сайтов, \n" +
                        "указанных в конфигурационном файле\n";
            }
        }

        if (pagesIndexing.contains(doc.location())) {
            return "Страница индексируется";
        }

        if (pagesIndexing.stream().anyMatch(p -> getSiteUrl(p).equals(siteUrl))) {
            return "Индексация невозможна. Индексируется страница с этого же сайта.";
        }

        pagesIndexing.add(doc.location());

        PageData pageData = pageRepository.findFirstByPathAndSite(getRelativeUrl(doc.location()), siteData);
        if (pageData != null) {
            deletePageData(pageData);
        }

        siteData.setStatus(SiteStatus.INDEXING);
        pageData = new PageData(siteData, getRelativeUrl(doc.location()),
                doc.connection().response().statusCode(), doc.html());

        List<PageData> pageDataList = new ArrayList<>();
        pageDataList.add(pageData);
        insertAllData(pageDataList, siteData);

        siteData.setStatus(SiteStatus.INDEXED);
        siteData.setStatusTime(LocalDateTime.now());
        siteRepository.save(siteData);
        pagesIndexing.remove(doc.location());

        return "OK";
    }

    public void insertAllData(List<PageData> pageDataList, SiteData siteData) {
        if (siteData.getStatus() == SiteStatus.FAILED) {
            return;
        }
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
                LemmaData lemmaData = lemmasToInsert
                        .stream()
                        .filter(l -> l.getLemma().equals(lemma))
                        .findFirst()
                        .orElse(null);

                if (lemmaData == null) {
                    lemmaData = lemmaDataList
                            .stream()
                            .filter(l -> l.getLemma().equals(lemma))
                            .findFirst()
                            .orElse(null);
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

    public Account getAccount() {
        return account;
    }

    public String getRelativeUrl(String url) {
        String[] parts = url.split("/", 4);
        if (parts.length < 4) {
            return "/";
        }
        return "/" + parts[3];
    }

    public String getSiteUrl(String url) {
        Pattern pattern = Pattern.compile("^https?://[^/]+");
        Matcher matcher = pattern.matcher(url);
        String siteUrl = null;
        if (matcher.find()) {
            siteUrl = matcher.group();
            if (!siteUrl.contains("www")) {
                String[] parts = siteUrl.split("//", 2);
                siteUrl = parts[0] + "//www." + parts[1];
            }
        }
        return siteUrl;
    }

    public SiteData findInConfigFile(String url) {
        String siteUrlShort = url.split("/")[2].replaceAll("www.", "");
        for (Site site : sites.getSites()) {
            if (site.getUrl().split("/")[2].replaceAll("www.", "").equals(siteUrlShort)) {
                SiteData siteData = new SiteData(SiteStatus.INDEXING, LocalDateTime.now(),
                        null, site.getUrl(), site.getName());
                siteRepository.save(siteData);
                return siteData;
            }
        }
        return null;
    }
}
