package searchengine.services;

import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.LemmaFinder;
import searchengine.dto.ErrorResponse;
import searchengine.model.LemmaData;
import searchengine.model.PageData;
import searchengine.model.SiteData;
import searchengine.Snippet;
import searchengine.dto.search.PageInfoItem;
import searchengine.dto.search.SearchResponse;
import searchengine.model.SiteStatus;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.util.*;

@Service
public class SearchServiceImpl implements SearchService {
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private String currentQuery = "";
    private String prevQuery;
    private final List<PageInfoItem> pageInfoItems = new ArrayList<>();
    private Set<String> queryLemmas;
    private final static int SNIPPET_LENGTH = 250;

    @Autowired
    public SearchServiceImpl(PageRepository pageRepository, SiteRepository siteRepository,
                             LemmaRepository lemmaRepository, IndexRepository indexRepository) {
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
    }

    @Override
    public ResponseEntity searchPages(String query, String siteUrl, int offset, int limit) {
        if (query.isBlank()) {
            return ResponseEntity.ok(new ErrorResponse("Задан пустой поисковый запрос"));
        }
        if (!currentQuery.isBlank()) {
            return ResponseEntity.ok(new ErrorResponse("Обрабатывается запрос \"" + currentQuery + "\""));
        }
        SiteData siteData = siteRepository.findFirstByUrl(siteUrl);
        if (siteUrl != null && (siteData == null || siteData.getStatus() == SiteStatus.INDEXING) ||
                siteUrl == null && siteRepository.existsByStatus(SiteStatus.INDEXING)) {
            return ResponseEntity.ok(new ErrorResponse("Сайт(ы) не проиндексирован(ы)"));
        }

        currentQuery = query;
        if (!currentQuery.equals(prevQuery)) {
            queryLemmas = LemmaFinder.getLemmaSet(query);

            List<LemmaData> lemmaDataList = getLemmasFromData(siteData);

            List<PageData> pageDataList = getPagesFromData(lemmaDataList, siteData);

            fillPagesInfo(lemmaDataList, pageDataList);

            prevQuery = currentQuery;
        }
        SearchResponse response = new SearchResponse();
        response.setCount(pageInfoItems.size());
        response.setData(getSubPageInfoList(offset, Math.min(pageInfoItems.size(), offset + limit)));
        response.setResult(true);
        currentQuery = "";
        return ResponseEntity.ok(response);
    }

    public List<LemmaData> getLemmasFromData(SiteData siteData) {
        List<LemmaData> lemmaDataList = new ArrayList<>();
        for (String lemma : queryLemmas) {
            LemmaData lemmaData;
            if (siteData == null) {
                List<LemmaData> lemmaAllSites = lemmaRepository.findAllByLemma(lemma);
                lemmaData = !lemmaAllSites.isEmpty()
                        ? new LemmaData(null, lemma,
                            lemmaAllSites.stream()
                                         .map(LemmaData::getFrequency)
                                         .reduce(Integer::sum)
                                         .get())
                        : null;
            } else {
                lemmaData = lemmaRepository.findFirstByLemmaAndSite(lemma, siteData);
            }
            if (lemmaData != null) {
                lemmaDataList.add(lemmaData);
            }
        }
        lemmaDataList.sort(Comparator.comparingInt(LemmaData::getFrequency));
        return lemmaDataList;
    }

    public List<PageData> getPagesFromData(List<LemmaData> lemmaDataList, SiteData siteData) {
        if (lemmaDataList.size() == 0) {
            return new ArrayList<>();
        }
        String lemma = lemmaDataList.get(0).getLemma();
        List<PageData> pageDataList = siteData != null
                ? pageRepository.findAllByLemmaAndSite(lemma, siteData, PageRequest.of(0, 500))
                : pageRepository.findAllByLemma(lemma, PageRequest.of(0, 500));

        for (int i = 1; i < lemmaDataList.size(); i++) {
            int pageIndex = 0;
            lemma = lemmaDataList.get(i).getLemma();
            while (pageIndex < pageDataList.size()) {
                if (indexRepository.existsByLemma_LemmaAndPage(lemma, pageDataList.get(pageIndex))) {
                    pageIndex++;
                } else {
                    pageDataList.remove(pageIndex);
                }
            }
        }
        return pageDataList;
    }

    public void fillPagesInfo(List<LemmaData> lemmaDataList, List<PageData> pageDataList) {
        pageInfoItems.clear();
        for (PageData pageData : pageDataList) {
            float absRelevance = 0;
            for (LemmaData lemmaData : lemmaDataList) {
                absRelevance += indexRepository.findFirstByLemma_LemmaAndPage(lemmaData.getLemma(), pageData).getRank();
            }
            PageInfoItem pageInfoItem = new PageInfoItem();
            pageInfoItem.setPageData(pageData);
            pageInfoItem.setRelevance(absRelevance);
            pageInfoItems.add(pageInfoItem);
        }
        if (pageInfoItems.size() > 0) {
            pageInfoItems.sort(Comparator.comparing(PageInfoItem::getRelevance).reversed());
            float maxAbsRelevance = pageInfoItems.get(0).getRelevance();
            pageInfoItems.forEach(p -> p.setRelevance(p.getRelevance() / maxAbsRelevance));
        }
    }

    public List<PageInfoItem> getSubPageInfoList(int fromIndex, int toIndex){
        List<PageInfoItem> subPageInfoList = pageInfoItems.subList(fromIndex, toIndex);
        for (PageInfoItem pageInfoItem: subPageInfoList){
            PageData pageData = pageInfoItem.getPageData();
            pageInfoItem.setSite(pageData.getSite().getUrl());
            pageInfoItem.setSiteName(pageData.getSite().getName());
            pageInfoItem.setUri(pageData.getPath());
            pageInfoItem.setTitle(Jsoup.parse(pageData.getContent()).title());
            pageInfoItem.setSnippet(getSnippetText(pageData, queryLemmas));
        }
        return subPageInfoList;
    }

    public String getSnippetText(PageData pageData, Set<String> queryLemmas) {

        String text = pageData.getContent();

        List<Snippet> snippetList = LemmaFinder.getSnippetList(text, queryLemmas);

        snippetList.sort(Comparator.comparingInt(f -> f.getQueryWordsIndexes().size()));
        String snippetText = "";
        int snippetIndex = snippetList.size() - 1;
        while (snippetIndex >= 0 && snippetText.length() < SNIPPET_LENGTH) {
            snippetText = snippetText
                    .concat(snippetList.get(snippetIndex)
                            .getFormattedText(SNIPPET_LENGTH - snippetText.length()))
                    .concat("|");
            snippetIndex--;
        }
        return snippetText;
    }
}
