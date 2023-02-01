package searchengine.Model;

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
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    @ManyToOne
    @JoinColumn(name = "site_id", nullable = false)
    private SiteData site;

    @Column(columnDefinition = "VARCHAR(255)", nullable = false)
    private String path;

    @Column(nullable = false)
    private int code;

    @Column(columnDefinition = "MEDIUMTEXT CHARACTER SET 'utf8mb4'", nullable = false)
    private String content;

    @OneToMany(mappedBy = "page", cascade = CascadeType.ALL)
    private List<IndexData> indexList;

    public PageData(SiteData site, String path, int code, String content) {
        this.site = site;
        this.path = path;
        this.code = code;
        this.content = content;
    }
}
