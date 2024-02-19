package com.ocean.proxy.server.proximal;


import com.ocean.proxy.server.proximal.service.AuthToDistal;
import com.ocean.proxy.server.proximal.service.HttpProxyServer;
import com.ocean.proxy.server.proximal.service.Socks4ProxyServer;
import com.ocean.proxy.server.proximal.service.Socks5ProxyServer;
import com.ocean.proxy.server.proximal.util.BytesUtil;
import com.ocean.proxy.server.proximal.util.CustomThreadFactory;
import com.ocean.proxy.server.proximal.util.SystemUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootApplication
@Slf4j
public class ProxyServerProximalApplication {

    private static final ExecutorService executorService = Executors.newCachedThreadPool(new CustomThreadFactory("proxyConn-"));

    public static void main(String[] args) throws Exception {
        SpringApplication.run(ProxyServerProximalApplication.class, args);

        Properties properties = loadProperties();
        AuthToDistal.properties = properties;
        //与远端服务进行认证，初始化连接，服务启动时执行
        boolean b = AuthToDistal.distalAuth();
        if (!b) {
            log.error("auth fail, close this program");
            return;
        } else {
            log.info("auth to distal success!");
        }
        String port = properties.getProperty("proxy.server.port");

        try (ServerSocket serverSocket = new ServerSocket(Integer.parseInt(port))) {
            log.info("Proxy Server is running on port " + port + ". support HTTP 、socks4 and socks5");
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
                        } else if (version == 67) {
                            log.info("use http proxy");
                            HttpProxyServer.handleClient(clientSocket);
                        } else {
                            log.error("error socks version: "+version);
                            byte[] aa = new byte[1024];
                            int len = clientInput.read(aa);
                            byte[] validData = BytesUtil.concatBytes(new byte[]{(byte) version}, BytesUtil.splitBytes(aa, 0, len));
                            log.info(new String(validData, StandardCharsets.UTF_8));
                            clientSocket.close();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    log.info("==================" + finalClientInfo + "==================");
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static Properties loadProperties() throws Exception {
        Properties properties = new Properties();
        Properties systemProperties = System.getProperties();
        Set<String> systemPropertiesNames = systemProperties.stringPropertyNames();
        if (systemPropertiesNames.size() > 0) {
            for (String systemPropertiesName : systemPropertiesNames) {
                properties.setProperty(systemPropertiesName,
                        systemProperties.getProperty(systemPropertiesName));
            }
        }
        InputStream resourceAsStream = ProxyServerProximalApplication.class.getClassLoader().getResourceAsStream("application.properties");
        if (resourceAsStream != null) {
            Properties propertiesFile = new Properties();
            propertiesFile.load(resourceAsStream);
            Set<String> filePropertiesNames = propertiesFile.stringPropertyNames();
            if (filePropertiesNames.size() > 0) {
                for (String filePropertiesName : filePropertiesNames) {
                    if (properties.get(filePropertiesName) == null) {
                        properties.setProperty(filePropertiesName, propertiesFile.getProperty(filePropertiesName));
                    }
                }
            }
        }
        return properties;
    }

}
