package com.ocean.proxy.server.distal.service;

import com.ocean.proxy.server.distal.util.BytesUtil;
import lombok.Data;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.Executors;

/**
 * <b>Description:</b>  <br/>
 * <b>@Author:</b> Ocean <br/>
 * <b>@DateTime:</b> 2023/12/8 18:06
 */
@Data
public class DataTransHandler {

    private ServerSocket localSocket;

    private Socket targetSocket;

    private byte[] token;

    private Cipher encryptCipher;
    private Cipher decryptCipher;


    public static void bindProximalAndTarget(ServerSocket localSocket, Socket targetSocket, byte[] token) throws Exception{
        DataTransHandler dataTransHandler = new DataTransHandler(localSocket, targetSocket, token);
        dataTransHandler.startTansData();
    }

    public DataTransHandler(ServerSocket localSocket, Socket targetSocket, byte[] token) throws Exception{
        this.localSocket = localSocket;
        this.targetSocket = targetSocket;
        this.token = token;
        encryptCipher = initEncryptCipher(token);
        decryptCipher = initDecryptCipher(token);
        System.out.println("create new socket for data trans, port:" + localSocket.getLocalPort());
    }

    public void startTansData() {
        Executors.newSingleThreadExecutor().execute(()->{
            try {
                Socket proximalSocket = localSocket.accept();
                System.out.println("client have been connected data trans socket:" + localSocket.getLocalPort());
                createProximalThread(proximalSocket, targetSocket);
                createTargetThread(targetSocket, proximalSocket);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void createProximalThread(Socket proximalSocket, Socket targetSocket) throws IOException {
        InputStream input = proximalSocket.getInputStream();
        Executors.newSingleThreadScheduledExecutor().execute(() -> {
            try {
                // 从客户端读取数据并发送到目标服务器
                int defaultLen = 1024;
                byte[] buffer = new byte[defaultLen];
                int bytesRead;
                while (!proximalSocket.isClosed() && (bytesRead = input.read(buffer)) != -1) {
                    byte[] validData = BytesUtil.splitBytes(buffer, 0, bytesRead);
                    buffer = new byte[defaultLen];
                    //解密
                    decryptData(validData);
                    OutputStream targetOutput = targetSocket.getOutputStream();
                    targetOutput.write(validData);
                }
            } catch (SocketException e) {
                if ("Connection reset".equals(e.getMessage())) {
                    // 处理Connection Reset状态
                    System.out.println("Connection reset by peer");
                } else {
                    // 处理其他SocketException
                    e.printStackTrace();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void createTargetThread(Socket targetSocket, Socket proximalSocket) throws IOException {
        InputStream input = targetSocket.getInputStream();
        Executors.newSingleThreadScheduledExecutor().execute(() -> {
            try {
                // 从客户端读取数据并发送到目标服务器
                int defaultLen = 1024;
                byte[] buffer = new byte[defaultLen];
                int bytesRead;
                while (!targetSocket.isClosed() && (bytesRead = input.read(buffer)) != -1) {
                    byte[] validData = BytesUtil.splitBytes(buffer, 0, bytesRead);
                    buffer = new byte[defaultLen];
                    //加密
                    encryptData(validData);
                    OutputStream proximalOutput = proximalSocket.getOutputStream();
                    proximalOutput.write(validData);
                }
            } catch (SocketException e) {
                if ("Connection reset".equals(e.getMessage())) {
                    // 处理Connection Reset状态
                    System.out.println("Connection reset by peer");
                } else {
                    // 处理其他SocketException
                    e.printStackTrace();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // private void encryptDecrypt(byte[] data) {
    //     for (int i = 0; i < data.length; i++) {
    //         if (i < token.length) {
    //             // data[i] ^= token[i % token.length];
    //             data[i] ^= token[i];
    //         }
    //     }
    // }

    private void encryptData(byte[] data) throws Exception {
        encryptCipher.doFinal(data);
    }

    private void decryptData(byte[] data) throws Exception {
        decryptCipher.doFinal(data);
    }

    private Cipher initEncryptCipher(byte[] token) throws Exception{
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        SecretKeySpec secretKeySpec = new SecretKeySpec(token, "AES");
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
        return cipher;
    }

    private Cipher initDecryptCipher(byte[] token) throws Exception{
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        SecretKeySpec secretKeySpec = new SecretKeySpec(token, "AES");
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
        return cipher;
    }

}
