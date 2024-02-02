package com.ocean.proxy.server.proximal.service;

import com.ocean.proxy.server.proximal.util.BytesUtil;
import com.ocean.proxy.server.proximal.util.CipherUtil;
import lombok.Data;
import lombok.Synchronized;
import org.apache.commons.lang3.StringUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * <b>Description:</b>  <br/>
 * <b>@Author:</b> Ocean <br/>
 * <b>@DateTime:</b> 2024/1/30 11:33
 */
public class AuthToDistal {

    //与对端建立连接与认证时使用的密钥
    private static final byte[] secretKey = "j8s1j9d0sa82@()U(@)$".getBytes(StandardCharsets.UTF_8);

    public static Properties properties;

    private static String username;

    private static String password;

    private static byte[] id;

    private static byte[] token;

    private static CipherUtil cipherUtil;

    public static byte[] getId() {
        return id;
    }

    public static byte[] getToken() {
        return token;
    }

    /**
     * 与远端服务直接建立连接，通过默认的密码进行交互
     *
     * @return
     * @throws Exception
     */
    @Synchronized
    public static boolean distalAuth() throws Exception {
        if (properties == null) {
            System.out.println("missing properties!");
            return false;
        }
        System.out.println("start auth with distal server");
        username = properties.getProperty("proxy.username");
        password = properties.getProperty("proxy.password");
        if (StringUtils.isAnyEmpty(username, password)) {
            System.out.println("username or password missing");
            return false;
        }
        String distalAddress = properties.getProperty("proxy.distal.address");
        String distalAuthPort = properties.getProperty("proxy.distal.auth.port");
        if (StringUtils.isAnyEmpty(distalAddress, distalAuthPort)) {
            System.out.println("distal address or port missing");
            return false;
        }
        System.out.println("auth distal " + distalAddress + ":" + distalAuthPort);
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
        byte[] result = new byte[25];
        if (inputStream.read(result) != -1) {
            encryptDecrypt(result);
            ByteBuffer buffer = ByteBuffer.wrap(result);
            if (buffer.get() != 1) {
                return false;
            }
            int distalConnectPort = buffer.getInt();
            id = new byte[4];
            buffer.get(id);
            token = new byte[16];
            buffer.get(token);
            System.out.println("receive distal auth response, connectPort:" + distalConnectPort +
                    ", id:" + BytesUtil.toNumberH(id) + ", token:" + BytesUtil.toHexString(token));
            cipherUtil = new CipherUtil(token);
            DistalServer.setDistalAddress(distalAddress);
            DistalServer.setDistalConnectPort(distalConnectPort);
            return true;
        }
        return false;
    }


    public static void encryptDecrypt(byte[] data) {
        for (int i = 0; i < data.length; i++) {
            data[i] ^= (secretKey[i % secretKey.length]);
        }
    }

    public static byte[] encryptData(byte[] data) throws Exception{
        return cipherUtil.encryptData(data);
    }

    public static byte[] decryptData(byte[] data) throws Exception{
        return cipherUtil.decryptData(data);
    }
}