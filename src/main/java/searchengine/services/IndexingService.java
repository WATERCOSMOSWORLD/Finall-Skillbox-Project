package searchengine.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.SitesList;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class IndexingService {

    private final AtomicBoolean isIndexing = new AtomicBoolean(false);
    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;

    public IndexingService(SitesList sitesList, SiteRepository siteRepository, PageRepository pageRepository) {
        this.sitesList = sitesList;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
    }

    public boolean isIndexing() {
        return isIndexing.get();
    }

    public boolean startIndexing() {
        return isIndexing.compareAndSet(false, true);
    }

    @Transactional
    public void performIndexing() {
        try {
            System.out.println("Индексация начата...");
            for (searchengine.config.Site siteConfig : sitesList.getSites()) {
                System.out.println("Удаление данных сайта: " + siteConfig.getUrl());
                deleteSiteData(siteConfig.getUrl());

                System.out.println("Создание новой записи для сайта: " + siteConfig.getUrl());
                createSiteEntry(siteConfig.getName(), siteConfig.getUrl());

                System.out.println("Индексация сайта: " + siteConfig.getUrl());
                Thread.sleep(1000);
            }
            System.out.println("Индексация завершена.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Индексация была прервана.");
        } finally {
            isIndexing.set(false);
        }
    }

    private void deleteSiteData(String siteUrl) {
        Site site = siteRepository.findByUrl(siteUrl);
        if (site != null) {
            pageRepository.deleteAllBySite(site);
            siteRepository.delete(site);
        }
    }

    private void createSiteEntry(String name, String url) {
        Site site = new Site();
        site.setName(name);
        site.setUrl(url);
        site.setStatus(Status.INDEXING);
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);
    }
}
