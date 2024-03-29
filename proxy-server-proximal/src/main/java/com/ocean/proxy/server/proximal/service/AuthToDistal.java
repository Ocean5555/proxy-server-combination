package com.ocean.proxy.server.proximal.service;

import com.ocean.proxy.server.proximal.util.BytesUtil;
import com.ocean.proxy.server.proximal.util.CipherUtil;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * <b>Description:</b>  <br/>
 * <b>@Author:</b> Ocean <br/>
 * <b>@DateTime:</b> 2024/1/30 11:33
 */
@Slf4j
public class AuthToDistal {

    //与对端建立连接与认证时使用的密钥
    private static byte[] secretKey = "j8s1j9d0sa82@()U(@)$".getBytes(StandardCharsets.UTF_8);

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
    public static boolean distalAuth(ConfigReader configReader) throws Exception {
        log.info("start auth with distal server");
        String distalAddress = configReader.getDistalAddress();
        Integer distalAuthPort = configReader.getDistalAuthPort();
        if (StringUtils.isEmpty(distalAddress) || distalAuthPort == null) {
            log.info("distal address or port missing");
            return false;
        }
        String authSecret = configReader.getProperties().getProperty("auth.secret");
        if (StringUtils.isNotEmpty(authSecret)) {
            secretKey = authSecret.getBytes(StandardCharsets.UTF_8);
        }
        log.info("auth distal " + distalAddress + ":" + distalAuthPort + ", use secret:" + new String(secretKey));
        Socket distalAuthSocket = new Socket(distalAddress, distalAuthPort);
        if (id != null && token != null) {
            log.info("id and token exist, check auth status.");
            boolean b = checkAuthStatus(distalAuthSocket);
            if (b) {
                log.info("auth status valid!");
                return true;
            }else{
                log.warn("auth status invalid! start auth again.");
            }
        }
        String username = configReader.getUsername();
        String password = configReader.getPassword();
        if (StringUtils.isAnyEmpty(username, password)) {
            log.info("username or password missing");
            return false;
        }
        //发送认证信息
        sendAuthInfo(distalAuthSocket, username, password);
        //接收认证结果
        int distalConnectPort = getAuthResponse(distalAuthSocket);
        if (distalConnectPort == 0) {
            return false;
        }
        DistalServer.init(configReader, distalConnectPort);
        return true;
    }

    private static boolean checkAuthStatus(Socket distalAuthSocket)throws Exception{
        InputStream inputStream = distalAuthSocket.getInputStream();
        OutputStream outputStream = distalAuthSocket.getOutputStream();
        byte[] version = new byte[]{0x02};
        byte[] requestData = BytesUtil.concatBytes(version, id, token);
        byte[] randomData = RandomUtils.nextBytes(RandomUtils.nextInt(20, 120));
        byte[] sendData = BytesUtil.concatBytes(requestData, randomData);
        encryptDecrypt(sendData);
        outputStream.write(sendData);
        byte[] result = new byte[1];
        if (inputStream.read(result) != -1) {
            encryptDecrypt(result);
            if (result[0] == 1) {
                //id和token有效
                return true;
            }
        }
        return false;
    }

    private static void sendAuthInfo(Socket distalAuthSocket, String username, String password) throws Exception{
        OutputStream outputStream = distalAuthSocket.getOutputStream();
        byte[] version = new byte[]{0x01};
        byte[] usernameBytes = username.getBytes(StandardCharsets.UTF_8);
        byte[] usernameLen = BytesUtil.toBytesH(usernameBytes.length);
        byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
        byte[] passwordLen = BytesUtil.toBytesH(passwordBytes.length);
        byte[] requestData = BytesUtil.concatBytes(version, usernameLen, usernameBytes, passwordLen, passwordBytes);
        byte[] randomData = RandomUtils.nextBytes(RandomUtils.nextInt(25, 120));
        byte[] sendData = BytesUtil.concatBytes(requestData, randomData);
        encryptDecrypt(sendData);
        outputStream.write(sendData);
    }

    private static int getAuthResponse(Socket distalAuthSocket)throws Exception{
        InputStream inputStream = distalAuthSocket.getInputStream();
        //1字节结果（1代表认证成功，其他代表失败），4字节proximal端标识，32字节token
        byte[] result = new byte[25];
        if (inputStream.read(result) != -1) {
            encryptDecrypt(result);
            ByteBuffer buffer = ByteBuffer.wrap(result);
            if (buffer.get() == 1) {
                int distalConnectPort = buffer.getInt();
                id = new byte[4];
                buffer.get(id);
                token = new byte[16];
                buffer.get(token);
                log.info("receive distal auth response, connectPort:" + distalConnectPort +
                        ", id:" + BytesUtil.toNumberH(id) + ", token:" + BytesUtil.toHexString(token));
                cipherUtil = new CipherUtil(token);
                return distalConnectPort;
            }
        }
        return 0;
    }

    public static void encryptDecrypt(byte[] data) {
        for (int i = 0; i < data.length; i++) {
            data[i] ^= (secretKey[i % secretKey.length]);
        }
    }

    public static byte[] encryptData(byte[] data) throws Exception {
        return cipherUtil.encryptDataAes(data);
    }

    public static byte[] decryptData(byte[] data) throws Exception {
        return cipherUtil.decryptDataAes(data);
    }
}
