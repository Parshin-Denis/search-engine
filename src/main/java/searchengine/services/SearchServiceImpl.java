package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.Model.LemmaData;
import searchengine.Model.PageData;
import searchengine.Model.SiteData;
import searchengine.config.SitesList;
import searchengine.dto.search.PageInfoItem;
import searchengine.dto.search.SearchResponse;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.util.*;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {
    private final SitesList sites;
    private final int  snippetLength = 250;
    @Autowired
    private PageRepository pageRepository;
    @Autowired
    private SiteRepository siteRepository;
    @Autowired
    private LemmaRepository lemmaRepository;
    @Autowired
    private IndexRepository indexRepository;

    @Override
    public SearchResponse searchPages(String query, String siteUrl, int offset, int limit) {
        SearchResponse response = new SearchResponse();

        SiteData siteData = siteRepository.findFirstByUrl(siteUrl);
        if (siteUrl != null && siteData == null){
            response.setResult(false);
            return response;
        }

        Set<String> queryLemmas = LemmaFinder.getLemmaSet(query);

        List<LemmaData> lemmaDataList = getLemmasFromData(queryLemmas, siteData);

        List<PageData> pageDataList = getPagesFromData(lemmaDataList, siteData);

        List<PageInfoItem> pageInfoItemList = new ArrayList<>();
        for (PageData pageData: pageDataList) {
            float absRelevance = 0;
            for (LemmaData lemmaData : lemmaDataList) {
                absRelevance += siteUrl != null
                        ? indexRepository.findFirstByLemmaAndPage(lemmaData, pageData).getRank()
                        : indexRepository.findFirstByLemma_LemmaAndPage(lemmaData.getLemma(), pageData).getRank();
            }
            PageInfoItem pageInfoItem = new PageInfoItem();
            pageInfoItem.setSite(pageData.getSite().getUrl());
            pageInfoItem.setSiteName(pageData.getSite().getName());
            pageInfoItem.setUri(pageData.getPath());
            pageInfoItem.setTitle(Jsoup.parse(pageData.getContent()).title());
            pageInfoItem.setSnippet(getSnippet(pageData, queryLemmas));
            pageInfoItem.setRelevance(absRelevance);
            pageInfoItemList.add(pageInfoItem);
        }
        if(pageInfoItemList.size() > 0) {
            pageInfoItemList.sort(Comparator.comparing(PageInfoItem::getRelevance).reversed());
            float maxAbsRelevance = pageInfoItemList.get(0).getRelevance();
            pageInfoItemList.forEach(p -> p.setRelevance(p.getRelevance() / maxAbsRelevance));
        }

        response.setCount(pageDataList.size());
        response.setData(pageInfoItemList.subList(offset, Math.min(pageDataList.size(), offset + limit)));
        response.setResult(true);
        return response;
    }

    public List<LemmaData> getLemmasFromData(Set<String> lemmaSet, SiteData siteData){
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

    public List<PageData> getPagesFromData(List<LemmaData> lemmaDataList, SiteData siteData){
        List<PageData> pageDataList = siteData != null
                ? pageRepository.findAllByLemmaAndSite(lemmaDataList.get(0).getLemma(), siteData.getId())
                : pageRepository.findAllByLemma(lemmaDataList.get(0).getLemma());

        for (int i = 1; i < lemmaDataList.size(); i++) {
            int pageIndex = 0;
            while (pageIndex < pageDataList.size()) {
                if (siteData != null
                        ?indexRepository.existsByLemmaAndPage(lemmaDataList.get(i), pageDataList.get(pageIndex))
                        :indexRepository.existsByLemma_LemmaAndPage(lemmaDataList.get(i).getLemma(),
                        pageDataList.get(pageIndex))) {
                    pageIndex++;
                } else {
                    pageDataList.remove(pageIndex);
                }
            }
        }
        return pageDataList;
    }

    public String getSnippet(PageData pageData, Set<String> queryLemmas) {

        String text = pageData.getContent();

        TreeMap<Integer, String> queryWordsIndexes = LemmaFinder.getWordIndexes(text, queryLemmas);

        List<Snippet> snippets = new ArrayList<>();
        Snippet snippet = null;
        for (Integer index : queryWordsIndexes.keySet()) {
            int startIndexFrag = text.substring(0, index).lastIndexOf(">") + 1;
            if (snippet == null || startIndexFrag != snippet.getBeginIndex()) {
                snippet = new Snippet(startIndexFrag);
                snippets.add(snippet);
            }
            snippet.getQueryWordsIndexes().put(index, queryWordsIndexes.get(index));
        }

        snippets.sort(Comparator.comparingInt(f -> f.getQueryWordsIndexes().size()));
        String snippetText = "";
        int snippetIndex = snippets.size() - 1;
        while (snippetIndex >= 0 && snippetText.length() < snippetLength) {
            snippetText += snippets.get(snippetIndex)
                    .getSnippetText(text, snippetLength - snippetText.length()) + " ";
            snippetIndex--;
        }
        return snippetText;
    }
}
