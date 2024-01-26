package com.ocean.proxy.server.distal;


import com.ocean.proxy.server.distal.service.Authentication;
import com.ocean.proxy.server.distal.service.ProxyServer;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProxyServerDistalApplication {

    public static void main(String[] args) throws Exception{
        Properties properties = loadProperties();
        String authPort = properties.getProperty("proxy.auth.port");
        if (StringUtils.isEmpty(authPort)) {
            authPort = "9110";
        }
        Authentication.startAuthServer(authPort);
    }

    private static Properties loadProperties() throws Exception{
        Properties properties = new Properties();
        Properties systemProperties = System.getProperties();
        Set<String> systemPropertiesNames = systemProperties.stringPropertyNames();
        if (systemPropertiesNames.size() > 0) {
            for (String systemPropertiesName : systemPropertiesNames) {
                properties.setProperty(systemPropertiesName,
                        systemProperties.getProperty(systemPropertiesName));
            }
        }
        InputStream resourceAsStream = ProxyServerDistalApplication.class.getClassLoader().getResourceAsStream("application.properties");
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
