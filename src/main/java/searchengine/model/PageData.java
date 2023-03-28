package searchengine.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.util.List;

@Entity
@Table(name = "page", indexes = @Index(columnList = "path"))
@Getter
@Setter
@NoArgsConstructor
public class PageData {
    public static final int MAX_LENGTH_PATH = 300;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "site_id", nullable = false)
    private SiteData site;

    @Column(columnDefinition = "VARCHAR(" + MAX_LENGTH_PATH + ")", nullable = false)
    private String path;

    @Column(nullable = false)
    private int code;

    @Column(columnDefinition = "MEDIUMTEXT CHARACTER SET 'utf8mb4'", nullable = false)
    private String content;

    @ManyToMany
    @JoinTable(name = "index", joinColumns = {@JoinColumn(name = "page_id")},
            inverseJoinColumns = {@JoinColumn(name = "lemma_id")})
    private List<LemmaData> lemmaDataList;

    public PageData(SiteData site, String path, int code, String content) {
        this.site = site;
        this.path = path;
        this.code = code;
        this.content = content;
    }
}
