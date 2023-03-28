package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.SiteData;
import searchengine.model.SiteStatus;

@Repository
public interface SiteRepository extends JpaRepository<SiteData, Integer> {

    SiteData findFirstByName(String name);
    SiteData findFirstByUrl(String url);
    boolean existsByStatus(SiteStatus status);
}
