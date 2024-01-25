package com.ocean.proxy.server.proximal;


import com.ocean.proxy.server.proximal.service.DistalHandler;
import com.ocean.proxy.server.proximal.service.Socks4ProxyServer;
import com.ocean.proxy.server.proximal.service.Socks5ProxyServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executors;

public class ProxyServerProximalApplication {

    public static Properties properties;

    public static void main(String[] args) throws Exception {
        properties = loadProperties();
        //与远端服务进行认证，初始化连接，服务启动时执行
        boolean b = DistalHandler.distalAuth(properties);
        if (!b) {
            System.out.println("auth fail, close this program");
            return;
        } else {
            System.out.println("auth to distal success!");
        }
        String port = properties.getProperty("proxy.server.port");
        try (ServerSocket serverSocket = new ServerSocket(Integer.parseInt(port))) {
            System.out.println("Proxy Server is running on port " + port + ". support socks4 and socks5");
            while (true) {
                // 等待客户端连接与认证
                Socket clientSocket = serverSocket.accept();
                String clientInfo = clientSocket.getInetAddress() + ":" + clientSocket.getPort();
                System.out.println("=================="+clientInfo+"==================");
                System.out.println("Accepted connection from " + clientInfo);
                // 开启一个线程处理客户端连接
                Executors.newSingleThreadScheduledExecutor().execute(() -> {
                    try {
                        InputStream clientInput = clientSocket.getInputStream();
                        int version = clientInput.read(); //版本， socks5的值是0x05, socks4的值是0x04
                        System.out.println("socks version:" + version);
                        if (version == 5) {
                            Socks5ProxyServer.handleClient(clientSocket);
                        } else if (version == 4) {
                            Socks4ProxyServer.handleClient(clientSocket);
                        } else {
                            System.out.println("error socks version!");
                            clientSocket.close();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    System.out.println("=================="+clientInfo+"==================");
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private static Properties loadProperties() throws Exception {
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
