package com.ocean.proxy.server.proximal.service;

import com.ocean.proxy.server.proximal.util.BytesUtil;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * <b>Description:</b>  <br/>
 * <b>@Author:</b> Ocean <br/>
 * <b>@DateTime:</b> 2023/12/11 11:44
 */
public class DistalHandler {

    //与对端建立连接与认证时使用的密钥
    private static final byte[] secretKey = "j8s1j9d0sa82@()U(@)$".getBytes(StandardCharsets.UTF_8);

    private static String distalAddress;

    private static String username;

    private static String password;

    private static String distalConnectPort;

    private static byte[] id;

    private static byte[] token;

    /**
     * 与远端服务直接建立连接，通过默认的密码进行交互
     *
     * @param properties
     * @return
     * @throws Exception
     */
    public static boolean distalAuth(Properties properties) throws Exception {
        System.out.println("start auth with distal server");
        username = properties.getProperty("proxy.username");
        password = properties.getProperty("proxy.password");
        if (StringUtils.isAnyEmpty(username, password)) {
            System.out.println("username or password missing");
            return false;
        }
        distalAddress = properties.getProperty("proxy.distal.address");
        String distalAuthPort = properties.getProperty("proxy.distal.auth.port");
        distalConnectPort = properties.getProperty("proxy.distal.connect.port");
        if (StringUtils.isAnyEmpty(distalAddress, distalAuthPort, distalConnectPort)) {
            System.out.println("distal address or port missing");
            return false;
        }
        Socket distalAuthSocket = new Socket(distalAddress, Integer.parseInt(distalAuthPort));
        InputStream inputStream = distalAuthSocket.getInputStream();
        OutputStream outputStream = distalAuthSocket.getOutputStream();
        byte[] version = new byte[]{0x01};
        byte[] usernameBytes = username.getBytes(StandardCharsets.UTF_8);
        byte[] usernameLen = BytesUtil.toBytesH(usernameBytes.length);
        byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
        byte[] passwordLen = BytesUtil.toBytesH(passwordBytes.length);
        byte[] requestData = BytesUtil.concatBytes(version, usernameLen, usernameBytes, passwordLen, passwordBytes);
        encryptDecrypt(requestData);
        outputStream.write(requestData);

        //1字节结果（1代表认证成功，其他代表失败），4字节proximal端标识，32字节token
        byte[] result = new byte[37];
        if (inputStream.read(result) != -1) {
            ByteBuffer buffer = ByteBuffer.wrap(result);
            if (buffer.get() != 1) {
                return false;
            }
            id = new byte[4];
            buffer.get(id);
            token = new byte[16];
            buffer.get(token);
            return true;
        }
        return false;
    }

    public static void encryptDecrypt(byte[] data) {
        for (int i = 0; i < data.length; i++) {
            data[i] ^= (secretKey[i % secretKey.length]);
        }
    }

    public static void createConnect(Socket clientSocket, String targetAddress, Integer port) throws Exception {
        System.out.println("create connect to target " + targetAddress + ":" + port);
        Socket distalConnectSocket = new Socket(distalAddress, Integer.parseInt(distalConnectPort));
        InputStream inputStream = distalConnectSocket.getInputStream();
        OutputStream outputStream = distalConnectSocket.getOutputStream();
        //发送数据给distal
        byte[] version = new byte[]{0x01};
        byte[] addressBytes = targetAddress.getBytes(StandardCharsets.UTF_8);
        byte[] addressLen = BytesUtil.toBytesH(addressBytes.length);
        byte[] portBytes = BytesUtil.toBytesH(port);
        byte[] data = BytesUtil.concatBytes(version, id, token, addressLen, addressBytes, portBytes);
        encryptDecrypt(data);
        outputStream.write(data);

        //接收distal的结果
        byte[] receiveData = new byte[1024];
        int len;
        if ((len = inputStream.read(receiveData)) != -1) {
            byte[] validData = BytesUtil.splitBytes(receiveData, 0, len);
            encryptDecrypt(validData);
            ByteBuffer buffer = ByteBuffer.wrap(validData);
            byte status = buffer.get();
            if (status == 1) {
                //成功
                int newPort = buffer.getInt();
                Socket distalTransSocket = new Socket(distalAddress, newPort);
                System.out.println("success connect distal trans port:" + newPort);
                DataTransHandler.bindClientAndDistal(clientSocket, distalTransSocket, token);
            } else {
                //失败
                clientSocket.close();
                throw new RuntimeException("create connect to distal fail");
            }
        } else {
            throw new RuntimeException("not receive data from distal");
        }
        distalConnectSocket.close();
    }

}
