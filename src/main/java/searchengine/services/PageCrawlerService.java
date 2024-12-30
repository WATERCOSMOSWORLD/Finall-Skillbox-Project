package searchengine.services;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

@Service
public class PageCrawlerService {

    private static final Logger logger = LoggerFactory.getLogger(PageCrawlerService.class);
    private static final int MAX_THREADS = Runtime.getRuntime().availableProcessors();
    private static final int MAX_DEPTH = 10;

    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final ForkJoinPool forkJoinPool = new ForkJoinPool(MAX_THREADS);
    private final Set<String> visitedUrls = ConcurrentHashMap.newKeySet();
    private final Set<String> alreadyLogged = ConcurrentHashMap.newKeySet(); // Для логирования только один раз

    public PageCrawlerService(PageRepository pageRepository, SiteRepository siteRepository) {
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
    }

    public int crawl(Site site) {
        logger.info("🌐 Начало индексации сайта: {}", site.getUrl());
        visitedUrls.clear();
        alreadyLogged.clear();
        CrawlTask rootTask = new CrawlTask(site.getUrl(), site, 0);
        forkJoinPool.invoke(rootTask);
        logger.info("✅ Индексация завершена. Всего уникальных ресурсов сохранено: {}", visitedUrls.size());
        return visitedUrls.size();
    }

    private class CrawlTask extends RecursiveAction {
        private final String url;
        private final Site site;
        private final int depth;

        public CrawlTask(String url, Site site, int depth) {
            this.url = normalizeUrl(url);
            this.site = site;
            this.depth = depth;
        }

        @Override
        protected void compute() {
            if (depth > MAX_DEPTH || visitedUrls.contains(url) || !url.startsWith(site.getUrl())) {
                logger.debug("⏭ Пропуск URL (глубина или дублирование): {}", url);
                return;
            }
            visitedUrls.add(url);

            try {
                Connection.Response response = Jsoup.connect(url)
                        .ignoreContentType(true)
                        .timeout(15000)
                        .followRedirects(true)
                        .execute();

                processResponse(site, url, response, depth);
            } catch (IOException e) {
                logAndSaveErrorPage(site, url, e.getMessage());
            }
        }

        private void processResponse(Site site, String url, Connection.Response response, int depth) throws IOException {
            String contentType = response.contentType();
            String finalUrl = normalizeUrl(response.url().toString());

            if (!finalUrl.equals(url)) {
                visitedUrls.add(finalUrl);
            }

            if (contentType == null) {
                logger.warn("⚠️ Пропуск: неизвестный тип содержимого (URL: {})", url);
                return;
            }

            if (contentType.startsWith("text/")) {
                Document document = response.parse();
                savePage(site, finalUrl, response.statusCode(), document.html());
                logger.info("📄 Проиндексирована страница: {}", url);

                Elements links = document.select("a[href]");
                List<CrawlTask> tasks = new ArrayList<>();
                for (var link : links) {
                    String nextUrl = normalizeUrl(link.absUrl("href"));
                    if (nextUrl.startsWith("tel:")) {
                        processPhoneNumber(site, nextUrl);
                    } else if (!visitedUrls.contains(nextUrl)) {
                        tasks.add(new CrawlTask(nextUrl, site, depth + 1));
                    }
                }
                invokeAll(tasks);
            } else if (contentType.startsWith("image/")) {
                savePage(site, finalUrl, response.statusCode(), "Изображение типа " + contentType);
                logger.info("🖼️ Проиндексировано изображение: {}", url);
            } else if (isSupportedFileType(contentType)) {
                savePage(site, finalUrl, response.statusCode(), "Файл типа " + contentType);
                logger.info("📁 Проиндексирован файл: {}", url);
            } else {
                logger.info("⏭ Пропуск файла с неподдерживаемым типом: {}", contentType);
            }
        }

        private boolean isSupportedFileType(String contentType) {
            return contentType.equals("application/pdf") ||
                    contentType.equals("application/msword") ||
                    contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document") ||
                    contentType.equals("application/vnd.ms-excel") ||
                    contentType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        }
    }

    private boolean savePage(Site site, String url, int statusCode, String content) {
        String relativePath = calculateRelativePath(site, url);
        synchronized (this) {
            if (pageRepository.existsBySiteAndPath(site, relativePath)) {
                logOnce("⏭ Уже сохранено: {}", url);
                return false;
            }
            Page page = new Page(site, relativePath, statusCode, content);
            pageRepository.save(page);
        }
        logger.info("✅ Сохранено: {} (Код: {})", url, statusCode);
        return true;
    }


    private void processPhoneNumber(Site site, String phoneUrl) {
        String phoneNumber = phoneUrl.replace("tel:", "");
        String content = "Телефонный номер: " + phoneNumber;
        synchronized (this) {
            if (pageRepository.existsBySiteAndPath(site, phoneUrl)) {
                logOnce("⏭ Номер телефона уже сохранён: {}", phoneNumber);
                return;
            }
            savePage(site, phoneUrl, 200, content);
        }
        logger.info("📞 Найден номер телефона: {}", phoneNumber);
    }

    private void logAndSaveErrorPage(Site site, String url, String errorMessage) {
        String content = "Ошибка при загрузке страницы: " + errorMessage;
        logger.error("❌ Ошибка при обработке URL: {} (Причина: {})", url, errorMessage);

        if (errorMessage.contains("Status=404")) {
            logger.warn("⚠️ Ресурс не найден (404): {}", url);
            return; // Не сохраняем в случае ошибки 404
        }

        savePage(site, url, 500, content);
    }


    private String calculateRelativePath(Site site, String url) {
        return url.replaceFirst(site.getUrl(), "").replaceAll("^/+", "/");
    }

    private String normalizeUrl(String url) {
        return url.replaceAll("/+$", "").toLowerCase();
    }

    private void logOnce(String message, String detail) {
        if (alreadyLogged.add(detail)) {
            logger.info(message, detail);
        }
    }
}
