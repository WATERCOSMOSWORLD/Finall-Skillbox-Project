package searchengine.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "site")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Site {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;  // Изменен на Integer вместо int

    @Column(columnDefinition = "ENUM('INDEXING', 'INDEXED', 'FAILED')", nullable = false)
    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "status_time", nullable = false)
    private LocalDateTime statusTime;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;  // Хранение информации об ошибке

    @Column(columnDefinition = "VARCHAR(255)", nullable = false, unique = true)
    private String url;  // Уникальный URL сайта

    @Column(columnDefinition = "VARCHAR(255)", nullable = false)
    private String name;  // Имя сайта

    @ManyToOne
    @JoinColumn(name = "parent_site_id", nullable = true)
    private Site parentSite;  // Связь с родительским сайтом (если есть)
}
