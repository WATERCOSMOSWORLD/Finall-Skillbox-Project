package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Site;

public interface SiteRepository extends JpaRepository<Site, Integer> {

    Site findByUrl(String url);

    Site findByName(String name);

    @Modifying
    @Transactional
    @Query("UPDATE Site s SET s.statusTime = CURRENT_TIMESTAMP WHERE s.id = :siteId")
    void updateStatusTime(Integer siteId);
}
