package com.ocean.proxy.server.proximal.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.Properties;
import java.util.Set;

/**
 * Description:
 *
 * @Author: Ocean
 * @DateTime: 2024/3/25 15:57
 */
@Slf4j
@Data
public class ConfigReader {

    private Properties properties;

    private String username;

    private String password;

    private String port;

    private String distalAddress;

    private Integer distalAuthPort;

    private Integer HttpPort;

    public ConfigReader() throws Exception{
        properties = loadProperties();
        if (properties == null) {
            log.info("missing properties!");
            throw new RuntimeException("missing properties!");
        }
        port = properties.getProperty("proxy.server.port");
        username = properties.getProperty("proxy.username");
        password = properties.getProperty("proxy.password");
        distalAddress = properties.getProperty("proxy.distal.address");
        distalAuthPort = Integer.parseInt(properties.getProperty("proxy.distal.auth.port"));
        HttpPort = Integer.parseInt(properties.getProperty("server.port"));
    }

    public Properties loadProperties() throws Exception {
        Properties properties = new Properties();
        Properties systemProperties = System.getProperties();
        Set<String> systemPropertiesNames = systemProperties.stringPropertyNames();
        if (systemPropertiesNames.size() > 0) {
            for (String systemPropertiesName : systemPropertiesNames) {
                properties.setProperty(systemPropertiesName,
                        systemProperties.getProperty(systemPropertiesName));
            }
        }
        InputStream resourceAsStream = ConfigReader.class.getClassLoader().getResourceAsStream("application.properties");
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
