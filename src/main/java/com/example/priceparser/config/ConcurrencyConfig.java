package com.example.priceparser.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class ConcurrencyConfig {

    /**
     * Кастомный пул парсинга. Очередь ограничена: при переполнении задачу выполняет вызывающий поток,
     * что притормаживает источник задач и не даёт очереди расти без предела.
     * Останавливает пул ParsingService, поэтому destroyMethod отключён.
     */
    @Bean(destroyMethod = "")
    public ThreadPoolExecutor parserExecutor(ParserProperties properties) {
        AtomicInteger threadNumber = new AtomicInteger();
        return new ThreadPoolExecutor(
                properties.poolSize(),
                properties.poolSize(),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(properties.queueCapacity()),
                task -> {
                    Thread thread = new Thread(task, "parser-worker-" + threadNumber.incrementAndGet());
                    // Поток наследует признак демона от создателя, а им бывает поток-планировщик
                    thread.setDaemon(false);
                    return thread;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
