package com.example.priceparser.service;

import com.example.priceparser.config.ParserProperties;
import com.example.priceparser.web.DemoShopController;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Добавляет страницы демо-магазина, чтобы результаты появлялись сразу после запуска. */
@Component
public class DemoDataInitializer implements ApplicationRunner {

    private final TrackedUrlService trackedUrls;
    private final ParserProperties properties;
    private final Environment environment;

    public DemoDataInitializer(TrackedUrlService trackedUrls, ParserProperties properties, Environment environment) {
        this.trackedUrls = trackedUrls;
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.seedDemoUrls()) {
            return;
        }
        String port = environment.getProperty("local.server.port", environment.getProperty("server.port", "8080"));
        for (long id : DemoShopController.productIds()) {
            trackedUrls.register("http://localhost:" + port + "/demo-shop/products/" + id);
        }
    }
}
