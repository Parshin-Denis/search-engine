package searchengine.model;

import lombok.*;

import javax.persistence.*;
import java.util.List;

@Entity
@Table(name = "lemma")
@Getter
@Setter
@NoArgsConstructor
public class LemmaData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "site_id", nullable = false)
    private SiteData site;

    @Column(columnDefinition = "VARCHAR(255)", nullable = false)
    private String lemma;

    @Column(nullable = false)
    private int frequency;

    public LemmaData(SiteData site, String lemma, int frequency) {
        this.site = site;
        this.lemma = lemma;
        this.frequency = frequency;
    }
}
