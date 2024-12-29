package searchengine.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import searchengine.services.IndexingService;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.StatisticsService;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;

    public ApiController(StatisticsService statisticsService, IndexingService indexingService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<Object> startIndexing() {
        if (indexingService.isIndexing()) {
            return ResponseEntity.badRequest().body("{\"result\": false, \"error\": \"Индексация уже запущена\"}");
        }

        if (indexingService.startIndexing()) {
            indexingService.performIndexing(); // Запуск сервиса индексации сайтов
            return ResponseEntity.ok("{\"result\": true}");
        } else {
            return ResponseEntity.badRequest().body("{\"result\": false, \"error\": \"Не удалось запустить индексацию\"}");
        }
    }
}
