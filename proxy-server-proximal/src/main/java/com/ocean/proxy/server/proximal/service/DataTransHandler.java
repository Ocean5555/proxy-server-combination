package com.ocean.proxy.server.proximal.service;

import com.ocean.proxy.server.proximal.util.BytesUtil;
import lombok.Data;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

    private Socket clientSocket;

    private Socket distalTransSocket;

    private byte[] token;

    private Cipher encryptCipher;
    private Cipher decryptCipher;

    public static void bindClientAndDistal(Socket clientSocket, Socket distalTransSocket, byte[] token) throws Exception {
        DataTransHandler dataTransHandler = new DataTransHandler(clientSocket, distalTransSocket, token);
        dataTransHandler.startTansData();
    }

    public DataTransHandler(Socket clientSocket, Socket distalTransSocket, byte[] token) throws Exception{
        this.clientSocket = clientSocket;
        this.distalTransSocket = distalTransSocket;
        this.token = token;
        encryptCipher = initEncryptCipher(token);
        decryptCipher = initDecryptCipher(token);
    }

    public void startTansData() throws IOException {
        createClientThread(clientSocket, distalTransSocket);
        createDistalThread(distalTransSocket, clientSocket);
    }

    private void createClientThread(Socket clientSocket, Socket distalTransSocket) throws IOException {
        InputStream input = clientSocket.getInputStream();
        OutputStream targetOutput = distalTransSocket.getOutputStream();
        Executors.newSingleThreadScheduledExecutor().execute(() -> {
            try {
                //源端与目标端数据传输
                int defaultLen = 1024;
                byte[] buffer = new byte[defaultLen];
                int bytesRead;
                while (!this.clientSocket.isClosed() && (bytesRead = input.read(buffer)) != -1) {
                    byte[] validData = BytesUtil.splitBytes(buffer, 0, bytesRead);
                    buffer = new byte[defaultLen];
                    //加密
                    encryptData(validData);
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

    private void createDistalThread(Socket distalTransSocket, Socket clientSocket) throws IOException {
        InputStream input = distalTransSocket.getInputStream();
        OutputStream targetOutput = clientSocket.getOutputStream();
        Executors.newSingleThreadScheduledExecutor().execute(() -> {
            try {
                //源端与目标端数据传输
                int defaultLen = 1024;
                byte[] buffer = new byte[defaultLen];
                int bytesRead;
                while (!this.clientSocket.isClosed() && (bytesRead = input.read(buffer)) != -1) {
                    byte[] validData = BytesUtil.splitBytes(buffer, 0, bytesRead);
                    buffer = new byte[defaultLen];
                    //解密
                    decryptData(validData);
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
