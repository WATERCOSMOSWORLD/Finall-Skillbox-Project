package searchengine.services;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class IndexingService {

    private final AtomicBoolean isIndexing = new AtomicBoolean(false);

    public boolean isIndexing() {
        // Проверяет, идет ли в данный момент процесс индексации
        return isIndexing.get();
    }

    public boolean startIndexing() {
        // Пытается запустить индексацию, если она еще не запущена
        if (isIndexing.compareAndSet(false, true)) {
            try {
                // Здесь размещается логика индексации
                performIndexing();
                return true;
            } finally {
                // После завершения индексации сбрасываем статус
                isIndexing.set(false);
            }
        }
        return false; // Если индексация уже была запущена
    }

    private void performIndexing() {
        // Основная логика полной индексации
        try {
            // Симуляция индексации
            Thread.sleep(5000); // Например, выполнение задачи занимает 5 секунд
            System.out.println("Индексация завершена.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Индексация была прервана.");
        }
    }
}
