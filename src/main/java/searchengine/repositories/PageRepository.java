package searchengine.repositories;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.Site;

public interface PageRepository extends JpaRepository<Page, Integer> {

    @Modifying
    @Transactional
    @Query("DELETE FROM Page p WHERE p.site.id = :siteId")
    int deleteAllBySiteId(@Param("siteId") Integer siteId);

    @Query("SELECT p FROM Page p WHERE p.path = :path AND p.site.id = :siteId")
    Page findByPathAndSiteId(@Param("path") String path, @Param("siteId") Integer siteId);

    boolean existsBySiteAndPath(Site site, String path);
}
