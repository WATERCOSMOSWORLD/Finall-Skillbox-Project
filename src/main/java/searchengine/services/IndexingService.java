package searchengine.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.SitesList;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class IndexingService {

    private static final Logger logger = LoggerFactory.getLogger(IndexingService.class);

    private final AtomicBoolean isIndexing = new AtomicBoolean(false);
    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final PageCrawlerService pageCrawlerService;

    public IndexingService(SitesList sitesList, SiteRepository siteRepository, PageRepository pageRepository, PageCrawlerService pageCrawlerService) {
        this.sitesList = sitesList;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.pageCrawlerService = pageCrawlerService;
    }

    public boolean isIndexing() {
        return isIndexing.get();
    }

    public boolean startIndexing() {
        if (!isIndexing.compareAndSet(false, true)) {
            logger.warn("Индексация уже запущена!");
            return false;
        }
        // Убираем дублирующиеся сайты
        List<searchengine.config.Site> uniqueSites = sitesList.getSites().stream()
                .distinct()
                .toList();
        sitesList.setSites(uniqueSites);
        logger.info("Индексация успешно запущена. Уникальных сайтов для индексации: {}", uniqueSites.size());
        return true;
    }

    @Transactional
    public void performIndexing() {
        String sessionId = UUID.randomUUID().toString();
        try {
            logger.info("=== НАЧАЛО ИНДЕКСАЦИИ САЙТОВ (Session ID: {}) ===", sessionId);
            List<CompletableFuture<Void>> futures = sitesList.getSites().stream()
                    .map(this::processSingleSiteAsync)
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            logger.info("=== ИНДЕКСАЦИЯ УСПЕШНО ЗАВЕРШЕНА (Session ID: {}) ===", sessionId);
        } catch (Exception e) {
            logger.error("[Session ID: {}] Ошибка в процессе индексации: {}", sessionId, e.getMessage(), e);
        } finally {
            isIndexing.set(false);
            logger.info("=== Завершение сессии индексации (Session ID: {}) ===", sessionId);
        }
    }

    @Async
    private CompletableFuture<Void> processSingleSiteAsync(searchengine.config.Site siteConfig) {
        return CompletableFuture.runAsync(() -> processSingleSite(siteConfig));
    }

    private void processSingleSite(searchengine.config.Site siteConfig) {
        try {
            logger.info("Обработка сайта: {}", siteConfig.getUrl());
            int deletedPages = deleteSiteData(siteConfig.getUrl());
            logger.info("Удалено старых страниц: {}", deletedPages);
            Site site = createSiteEntry(siteConfig.getName(), siteConfig.getUrl());
            logger.info("Создана новая запись сайта: {}", site.getUrl());
            pageCrawlerService.crawl(site); // Метод crawl больше не возвращает int
            int indexedPages = pageCrawlerService.getVisitedCount(); // Новый метод для получения количества страниц
            logger.info("Проиндексировано страниц: {}", indexedPages);
            site.setStatus(Status.INDEXED);
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
        } catch (Exception e) {
            logger.error("Ошибка при обработке сайта {}: {}", siteConfig.getUrl(), e.getMessage(), e);
            handleFailedSite(siteConfig.getUrl(), e.getMessage());
        }
    }


    @Transactional
    private int deleteSiteData(String siteUrl) {
        Site site = siteRepository.findByUrl(siteUrl);
        if (site == null) {
            logger.warn("Сайт не найден в базе данных: {}", siteUrl);
            return 0;
        }
        int deletedPages = pageRepository.deleteAllBySiteId(site.getId());
        siteRepository.deleteById(site.getId());
        logger.info("Удалена запись сайта: {} и связанных страниц: {}", siteUrl, deletedPages);
        return deletedPages;
    }

    private Site createSiteEntry(String name, String url) {
        Site site = new Site();
        site.setName(name);
        site.setUrl(url);
        site.setStatus(Status.INDEXING);
        site.setStatusTime(LocalDateTime.now());
        return siteRepository.save(site);
    }

    private void handleFailedSite(String url, String errorMessage) {
        Site site = siteRepository.findByUrl(url);
        if (site != null) {
            site.setStatus(Status.FAILED);
            site.setLastError(errorMessage);
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
        }
    }
}