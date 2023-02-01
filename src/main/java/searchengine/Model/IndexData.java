package searchengine.Model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Table(name = "lemma2page")
@Getter
@Setter
@NoArgsConstructor
public class IndexData {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    @ManyToOne
    @JoinColumn(name = "page_id", nullable = false)
    private PageData page;

    @ManyToOne
    @JoinColumn(name = "lemma_id", nullable = false)
    private LemmaData lemma;

    @Column(name = "amount", nullable = false)
    private float rank;

    public IndexData(PageData page, LemmaData lemma, float rank) {
        this.page = page;
        this.lemma = lemma;
        this.rank = rank;
    }
}
