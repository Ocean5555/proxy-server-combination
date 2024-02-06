package com.ocean.proxy.server.proximal.service;


import com.ocean.proxy.server.proximal.handler.DistalHandler;
import com.ocean.proxy.server.proximal.util.BytesUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <b>Description:</b>  <br/>
 * <b>@Author:</b> Ocean <br/>
 * <b>@DateTime:</b> 2024/2/2 13:52
 */
public class HttpProxyServer {

    private static final Pattern pattern = Pattern.compile("Host: (.+:\\d+)[\r]\n");

    public static void handleClient(Socket clientSocket) {
        try {
            InputStream clientInput = clientSocket.getInputStream();
            OutputStream outputStream = clientSocket.getOutputStream();
            byte[] data = new byte[1024];
            int len = clientInput.read(data);
            data = BytesUtil.splitBytes(data, 0, len);
            //第一个字节是67
            byte[] validData = BytesUtil.concatBytes(new byte[]{0x43}, data);
            String requestData = new String(validData, StandardCharsets.UTF_8);
            Matcher matcher = pattern.matcher(requestData);
            if (matcher.find()) {
                String host = matcher.group(1);
                System.out.println("target :"+ host);
                String[] split = host.split(":");
                DistalHandler distalHandler = new DistalHandler(clientSocket, split[0], Integer.parseInt(split[1]));
                DistalServer.createDistalConnect(distalHandler);
                outputStream.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            }else{
                System.out.println("not found target host!");
                clientSocket.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
            try {
                clientSocket.close();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }
}
