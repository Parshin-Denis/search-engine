package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.LemmaFinder;
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
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {
    @Autowired
    private PageRepository pageRepository;
    @Autowired
    private SiteRepository siteRepository;
    @Autowired
    private LemmaRepository lemmaRepository;
    @Autowired
    private IndexRepository indexRepository;
    private String prevQuery;
    private int pagesCount;
    private List<PageInfoItem> pageInfoItems = new ArrayList<>();
    private final int snippetLength = 250;

    @Override
    public SearchResponse searchPages(String query, String siteUrl, int offset, int limit) {
        SearchResponse response = new SearchResponse();
        String currentQuery = siteUrl + " " + query;
        if (!currentQuery.equals(prevQuery)) {
            SiteData siteData = siteRepository.findFirstByUrl(siteUrl);
            if (siteUrl != null && (siteData == null || siteData.getStatus() == SiteStatus.INDEXING) ||
                siteUrl == null && siteRepository.existsByStatus(SiteStatus.INDEXING)) {
                response.setResult(false);
                return response;
            }

            Set<String> queryLemmas = LemmaFinder.getLemmaSet(query);

            List<LemmaData> lemmaDataList = getLemmasFromData(queryLemmas, siteData);

            List<PageData> pageDataList = getPagesFromData(lemmaDataList, siteData);

            fillPagesInfo(pageDataList, lemmaDataList, siteData, queryLemmas);

            pagesCount = pageDataList.size();
            prevQuery = currentQuery;
        }
        response.setCount(pagesCount);
        response.setData(pageInfoItems.subList(offset, Math.min(pagesCount, offset + limit)));
        response.setResult(true);
        return response;
    }

    public List<LemmaData> getLemmasFromData(Set<String> lemmaSet, SiteData siteData) {
        List<LemmaData> lemmaDataList = new ArrayList<>();
        for (String lemma : lemmaSet) {
            LemmaData lemmaData = null;
            if (siteData == null) {
                List<LemmaData> lemmas = lemmaRepository.findAllByLemma(lemma);
                if (lemmas.size() > 0) {
                    lemmaData = new LemmaData(null, lemma,
                            lemmas.stream()
                                    .map(LemmaData::getFrequency)
                                    .reduce(Integer::sum).get());
                }
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
        if(lemmaDataList.size() == 0){
            return new ArrayList<>();
        }
        List<PageData> pageDataList = siteData != null
                ? pageRepository.findAllByLemmaAndSite(lemmaDataList.get(0).getLemma(), siteData.getId())
                : pageRepository.findAllByLemma(lemmaDataList.get(0).getLemma());

        for (int i = 1; i < lemmaDataList.size(); i++) {
            int pageIndex = 0;
            while (pageIndex < pageDataList.size()) {
                if (siteData != null
                        ? indexRepository.existsByLemmaAndPage(lemmaDataList.get(i), pageDataList.get(pageIndex))
                        : indexRepository.existsByLemma_LemmaAndPage(lemmaDataList.get(i).getLemma(),
                        pageDataList.get(pageIndex))) {
                    pageIndex++;
                } else {
                    pageDataList.remove(pageIndex);
                }
            }
        }
        return pageDataList;
    }

    public void fillPagesInfo(List<PageData> pageDataList, List<LemmaData> lemmaDataList,
                              SiteData siteData, Set<String> queryLemmas){
        pageInfoItems.clear();
        for (PageData pageData : pageDataList) {
            float absRelevance = 0;
            for (LemmaData lemmaData : lemmaDataList) {
                absRelevance += siteData != null
                        ? indexRepository.findFirstByLemmaAndPage(lemmaData, pageData).getRank()
                        : indexRepository.findFirstByLemma_LemmaAndPage(lemmaData.getLemma(), pageData).getRank();
            }
            PageInfoItem pageInfoItem = new PageInfoItem();
            pageInfoItem.setSite(pageData.getSite().getUrl());
            pageInfoItem.setSiteName(pageData.getSite().getName());
            pageInfoItem.setUri(pageData.getPath());
            pageInfoItem.setTitle(Jsoup.parse(pageData.getContent()).title());
            pageInfoItem.setSnippet(getSnippetText(pageData, queryLemmas));
            pageInfoItem.setRelevance(absRelevance);
            pageInfoItems.add(pageInfoItem);
        }
        if (pageInfoItems.size() > 0) {
            pageInfoItems.sort(Comparator.comparing(PageInfoItem::getRelevance).reversed());
            float maxAbsRelevance = pageInfoItems.get(0).getRelevance();
            pageInfoItems.forEach(p -> p.setRelevance(p.getRelevance() / maxAbsRelevance));
        }
    }

    public String getSnippetText(PageData pageData, Set<String> queryLemmas) {

        String text = pageData.getContent();

        List<Snippet> snippetList = LemmaFinder.getSnippetList(text, queryLemmas);

        snippetList.sort(Comparator.comparingInt(f -> f.getQueryWordsIndexes().size()));
        String snippetText = "";
        int snippetIndex = snippetList.size() - 1;
        while (snippetIndex >= 0 && snippetText.length() < snippetLength) {
            snippetText = snippetText
                    .concat(snippetList.get(snippetIndex)
                            .getFormattedText(snippetLength - snippetText.length()))
                    .concat("|");
            snippetIndex--;
        }
        return snippetText;
    }
}
