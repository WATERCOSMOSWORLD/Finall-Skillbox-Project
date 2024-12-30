package searchengine.services;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

@Service
public class PageCrawlerService {

    private static final Logger logger = LoggerFactory.getLogger(PageCrawlerService.class);
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final Set<String> visitedUrls = new HashSet<>();

    public PageCrawlerService(PageRepository pageRepository, SiteRepository siteRepository) {
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
    }

    public int crawl(Site site) {
        logger.info("=== Начало обхода сайта: {} ===", site.getUrl());
        visitedUrls.clear();
        int savedPages = bfsCrawl(site);
        logger.info("=== Обход завершен. Сохранено страниц: {} ===", savedPages);
        return savedPages;
    }

    private int bfsCrawl(Site site) {
        Queue<String> urlQueue = new LinkedList<>();
        urlQueue.add(site.getUrl());
        int savedPagesCount = 0;

        while (!urlQueue.isEmpty()) {
            String currentUrl = urlQueue.poll();

            if (visitedUrls.contains(currentUrl)) {
                logger.debug("Пропущен ранее обработанный URL: {}", currentUrl);
                continue;
            }
            visitedUrls.add(currentUrl);

            try {
                Connection.Response response = Jsoup.connect(currentUrl)
                        .ignoreContentType(true)
                        .timeout(15000) // Увеличенное время ожидания
                        .followRedirects(true)
                        .execute();

                // Обновляем поле statusTime на каждой итерации обхода
                updateSiteStatusTime(site);

                processResponse(site, currentUrl, response, urlQueue);
                savedPagesCount++;
            } catch (IOException e) {
                logAndSaveErrorPage(site, currentUrl, e.getMessage());
            }
        }
        return savedPagesCount;
    }

    private void processResponse(Site site, String url, Connection.Response response, Queue<String> urlQueue) throws IOException {
        int statusCode = response.statusCode();
        String contentType = response.contentType();

        if (contentType != null) {
            if (contentType.startsWith("text/")) {
                Document document = response.parse();
                savePage(site, url, statusCode, document.html());
                enqueueLinks(site, document, urlQueue);
            } else if (contentType.startsWith("application/pdf")) {
                saveFile(site, url, statusCode, response.bodyAsBytes(), "pdf");
            } else if (contentType.startsWith("image/")) {
                saveFile(site, url, statusCode, response.bodyAsBytes(), "images");
            } else {
                logger.warn("Пропущен неподдерживаемый тип контента: {} для URL {}", contentType, url);
            }
        } else {
            logger.warn("Пропущен URL без типа контента: {}", url);
        }
    }

    private void enqueueLinks(Site site, Document document, Queue<String> urlQueue) {
        Elements links = document.select("a[href]");
        logger.debug("Обнаружено ссылок на странице {}: {}", site.getUrl(), links.size());

        for (Element link : links) {
            String nextUrl = link.absUrl("href");
            if (isValidUrl(nextUrl, site.getUrl())) {
                urlQueue.add(nextUrl);
                logger.debug("Добавлен в очередь URL: {}", nextUrl);
            } else {
                logger.debug("Пропущен URL: {}", nextUrl);
            }
        }
    }

    private boolean savePage(Site site, String url, int statusCode, String content) {
        String relativePath = calculateRelativePath(site, url);
        Page page = new Page(site, relativePath, statusCode, content);
        pageRepository.save(page);
        logger.info("Сохранена страница: {} (код: {})", url, statusCode);
        return true;
    }

    private boolean saveFile(Site site, String url, int statusCode, byte[] content, String folder) {
        String relativePath = calculateRelativePath(site, url, folder);

        try {
            Files.createDirectories(Paths.get(relativePath).getParent());
            Files.write(Paths.get(relativePath), content);
            logger.info("Сохранён файл: {} (код: {}, папка: {})", url, statusCode, folder);
            savePage(site, url, statusCode, "Файл сохранён: " + relativePath);
            return true;
        } catch (IOException e) {
            logger.error("Ошибка при сохранении файла {}: {}", url, e.getMessage(), e);
            return false;
        }
    }

    private void logAndSaveErrorPage(Site site, String url, String errorMessage) {
        String content = "Ошибка при загрузке страницы: " + errorMessage;
        savePage(site, url, 500, content);
        logger.error("Ошибка при обработке URL {}: {}", url, errorMessage);
    }

    private String calculateRelativePath(Site site, String url) {
        return calculateRelativePath(site, url, "/");
    }

    private String calculateRelativePath(Site site, String url, String defaultPath) {
        String relativePath = url.replaceFirst(site.getUrl(), "").replaceAll("^/+", "/");
        return relativePath.isEmpty() ? defaultPath : defaultPath + relativePath;
    }

    private boolean isValidUrl(String url, String baseUrl) {
        return url != null &&
                (url.startsWith(baseUrl) || url.startsWith(baseUrl + "/")) &&
                !url.contains("#") &&
                !url.matches(".*\\.(css|js|ico|svg|woff|woff2|ttf|eot|mp4|avi|mkv)$");
    }

    private void updateSiteStatusTime(Site site) {
        try {
            siteRepository.updateStatusTime(site.getId());
            logger.debug("Поле statusTime обновлено для сайта: {}", site.getUrl());
        } catch (Exception e) {
            logger.error("Ошибка при обновлении statusTime для сайта {}: {}", site.getUrl(), e.getMessage());
        }
    }
}
