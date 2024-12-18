package com.ocean.proxy.server.proximal;


import com.ocean.proxy.server.proximal.service.*;
import com.ocean.proxy.server.proximal.util.CustomThreadFactory;
import com.ocean.proxy.server.proximal.util.SystemUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class ProxyServerProximalApplication {

    private static final ExecutorService executorService = Executors.newCachedThreadPool(new CustomThreadFactory("ClientConn-"));

    public static void main(String... args) throws Exception {
        ConfigReader configReader = new ConfigReader();
        //与远端服务进行认证，初始化连接，服务启动时执行
        boolean b = AuthToDistal.distalAuth(configReader);
        if (!b) {
            log.error("auth fail, close this program");
            return;
        } else {
            log.info("auth to distal success!");
        }
        String port = configReader.getPort();
        String systemProxySet = "127.0.0.1" + ":" + port;
        if (args.length > 0) {
            String isPac = args[0];
            if ("pac".equalsIgnoreCase(isPac)) {
                SystemUtil.isPac = true;
                Integer httpPort = configReader.getHttpPort();
                WebService.startWebservice(httpPort);
                systemProxySet = "http://127.0.0.1:" + httpPort + "/pac/1.pac";
            }
        }
        SystemUtil.startSystemProxy(systemProxySet);
        new Thread(() -> startHttpProxyServer(Integer.parseInt(port)), "HttpProxyServerThread").start();
        new Thread(() -> startProxyServer(Integer.parseInt(port)+1), "SocksProxyServerThread").start();
    }

    private static void startProxyServer(Integer port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log.info("Proxy Server is running on port " + port + ". support socks4 and socks5");
            while (true) {
                // 等待客户端连接与认证
                Socket clientSocket = serverSocket.accept();
                String clientInfo = clientSocket.getInetAddress() + ":" + clientSocket.getPort();
                if (clientSocket.getInetAddress().isLoopbackAddress()) {
                    String taskName = SystemUtil.getTaskByPort(clientSocket.getPort());
                    if (StringUtils.isNotEmpty(taskName)) {
                        clientInfo = taskName;
                    }
                }
                log.info("==================" + clientInfo + "==================");
                log.info("Accepted connection from " + clientInfo);
                // 开启一个线程处理客户端连接
                String finalClientInfo = clientInfo;
                executorService.execute(() -> {
                    try {
                        InputStream clientInput = clientSocket.getInputStream();
                        int version = clientInput.read(); //版本， socks5的值是0x05, socks4的值是0x04
                        while (version == -1) {
                            version = clientInput.read();
                        }
                        if (version == 5) {
                            log.info("use socks version: " + version);
                            Socks5ProxyServer.handleClient(clientSocket);
                        } else if (version == 4) {
                            log.info("use socks version: " + version);
                            Socks4ProxyServer.handleClient(clientSocket);
                        } else {
                            log.error("error proxy version");
                        }
                    } catch (Exception e) {
                        log.error("", e);
                    }
                    log.info("==================" + finalClientInfo + "==================");
                });
            }
        } catch (Exception e) {
            log.error(" SOCKS PROXY SERVER ERROR ", e);

        }
    }

    private static void startHttpProxyServer(Integer port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log.info("Proxy Server is running on port " + port + ". support HTTP and HTTPS");
            while (true) {
                // 等待客户端连接与认证
                Socket clientSocket = serverSocket.accept();
                String clientInfo = clientSocket.getInetAddress() + ":" + clientSocket.getPort();
                if (clientSocket.getInetAddress().isLoopbackAddress()) {
                    String taskName = SystemUtil.getTaskByPort(clientSocket.getPort());
                    if (StringUtils.isNotEmpty(taskName)) {
                        clientInfo = taskName;
                    }
                }
                log.info("==================" + clientInfo + "==================");
                log.info("Accepted connection from " + clientInfo);
                // 开启一个线程处理客户端连接
                String finalClientInfo = clientInfo;
                executorService.execute(() -> {
                    HttpProxyServer.handleClient(clientSocket);
                    log.info("==================" + finalClientInfo + "==================");
                });
            }
        } catch (Exception e) {
            log.error(" HTTP PROXY SERVER ERROR ", e);
        }
    }
}
