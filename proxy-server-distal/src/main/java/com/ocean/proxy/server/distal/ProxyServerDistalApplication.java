package com.ocean.proxy.server.distal;


import com.ocean.proxy.server.distal.service.AuthServer;
import com.ocean.proxy.server.distal.service.ProxyServer;
import org.apache.commons.lang3.StringUtils;

import java.io.InputStream;
import java.util.Properties;
import java.util.Set;

public class ProxyServerDistalApplication {

    public static void main(String[] args) throws Exception{
        Properties properties = loadProperties();
        Integer authPort = Integer.parseInt(properties.getProperty("proxy.auth.port"));
        Integer proxyPort = Integer.parseInt(properties.getProperty("proxy.proxy.port"));
        String authSecret = properties.getProperty("auth.secret");
        AuthServer.startAuthServer(authPort, proxyPort, authSecret);
        ProxyServer.startProxyServer(proxyPort);
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
