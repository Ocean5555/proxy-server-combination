package com.ocean.proxy.server.proximal.util;

/**
 * <b>Description:</b>  <br/>
 * <b>@Author:</b> Ocean <br/>
 * <b>@DateTime:</b> 2024/1/24 16:04
 */
public class IpUtil {

    public static String bytesToIpAddress(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%d.", (b & 0xFF)));
        }
        return result.deleteCharAt(result.length() - 1).toString();
    }
}
