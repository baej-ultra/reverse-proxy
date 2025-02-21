package org.baej;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Application {

    private static final ExecutorService executorService = Executors.newCachedThreadPool();

    public static void main(String[] args) {
        executorService.submit(() -> new ProxyHandler(5555, "localhost", 8080));
        executorService.submit(() -> new ProxyHandler(5556, "localhost", 8081));
    }
}
