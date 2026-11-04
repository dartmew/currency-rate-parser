package Univer.Parser.service;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Service
public class LoggingService {
    private final BlockingQueue<String> logQueue = new LinkedBlockingQueue<>();
    private final String logFilePath = "parser_history.log";

    @PostConstruct
    public void init() {
        Thread daemonLogger = new Thread(() -> {
            while (true) {
                try {
                    String message = logQueue.take(); 
                    writeToFile(message);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        
        daemonLogger.setDaemon(true);
        daemonLogger.setName("Daemon-File-Logger");
        daemonLogger.start();
    }

    public void log(String message) {
        logQueue.offer(LocalDateTime.now() + " | " + message);
    }

    private void writeToFile(String message) {
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(logFilePath, true), StandardCharsets.UTF_8))) {
            writer.println(message);
        } catch (IOException e) {
            System.err.println("Ошибка записи в файл: " + e.getMessage());
        }
    }
}