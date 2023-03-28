package searchengine.repositories;

import org.springframework.data.domain.Pageable;
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
    @Query(value = "SELECT p FROM PageData p\n" +
            "JOIN IndexData i ON p = i.page \n" +
            "JOIN LemmaData l ON l = i.lemma \n" +
            "JOIN SiteData s ON s = p.site \n" +
            "WHERE l.lemma = :lemma AND s = :siteData")
    List<PageData> findAllByLemmaAndSite(String lemma, SiteData siteData, Pageable pageable);
    @Query(value = "SELECT p FROM PageData p\n" +
            "JOIN IndexData i ON p.id = i.page.id\n" +
            "JOIN LemmaData l ON l.id = i.lemma.id \n" +
            "WHERE l.lemma = :lemma")
    List<PageData> findAllByLemma(String lemma, Pageable pageable);
}
