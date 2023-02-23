package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.IndexData;
import searchengine.model.LemmaData;
import searchengine.model.PageData;

public interface IndexRepository extends JpaRepository<IndexData, Integer> {

    IndexData findFirstByLemmaAndPage(LemmaData lemmaData, PageData pageData);
    IndexData findFirstByLemma_LemmaAndPage(String lemma, PageData pageData);
    boolean existsByLemmaAndPage(LemmaData lemmaData, PageData pageData);
    boolean existsByLemma_LemmaAndPage(String lemma, PageData pageData);
}
