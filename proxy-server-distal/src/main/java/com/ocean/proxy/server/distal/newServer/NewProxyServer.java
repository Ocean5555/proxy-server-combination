package com.ocean.proxy.server.distal.newServer;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Description:
 *
 * @author Ocean
 * datetime: 2024/12/17 10:37
 */
@Slf4j
public class NewProxyServer {

    public static void startProxyServer(Integer proxyPort) {
        ExecutorService executorService = Executors.newCachedThreadPool();
        try (ServerSocket serverSocket = new ServerSocket(proxyPort)) {
            log.info("Server started on {}. Waiting for client connection...", proxyPort);
            while (true) {
                // 等待客户端连接
                Socket clientSocket = serverSocket.accept();
                log.info("Client connected: " + clientSocket.getInetAddress() + ":" + clientSocket.getPort());
                executorService.execute(() -> {
                    try {
                        NewProxyHandler newProxyHandler = new NewProxyHandler(clientSocket);
                        newProxyHandler.start();
                    } catch (Exception e) {
                        log.error("client data process error.", e);
                        try {
                            clientSocket.close();
                        } catch (IOException ignore) {
                        }
                    }
                });
            }
        } catch (Exception e) {
            log.error("", e);
        } finally {
            log.info("proxy server stopped.");
        }

    }
}
