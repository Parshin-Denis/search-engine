package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.PageData;
import searchengine.model.SiteData;

import java.util.List;

@Repository
public interface PageRepository extends JpaRepository<PageData, Integer> {
    boolean existsByPathAndSite(String path, SiteData siteData);
    PageData findFirstByPathAndSite(String path, SiteData siteData);
    List<PageData> findFirst500BySite(SiteData siteData);
    int countBySite(SiteData siteData);
    @Query(value = "SELECT p.* FROM page p\n" +
            "JOIN lemma2page lp ON p.id = lp.page_id\n" +
            "JOIN lemma l ON l.id = lp.lemma_id \n" +
            "JOIN site s ON s.id = p.site_id\n" +
            "WHERE l.lemma = :lemma AND s.id = :siteId LIMIT 500", nativeQuery = true)
    List<PageData> findAllByLemmaAndSite(String lemma, int siteId);
    @Query(value = "SELECT p.* FROM page p\n" +
            "JOIN lemma2page lp ON p.id = lp.page_id\n" +
            "JOIN lemma l ON l.id = lp.lemma_id \n" +
            "WHERE l.lemma = :lemma LIMIT 500", nativeQuery = true)
    List<PageData> findAllByLemma(String lemma);
}
