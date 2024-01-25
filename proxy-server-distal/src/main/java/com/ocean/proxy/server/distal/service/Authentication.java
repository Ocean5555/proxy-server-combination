package com.ocean.proxy.server.distal.service;

import com.ocean.proxy.server.distal.util.BytesUtil;
import lombok.Synchronized;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.chrono.ThaiBuddhistChronology;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * <b>Description:</b>  <br/>
 * <b>@Author:</b> Ocean <br/>
 * <b>@DateTime:</b> 2023/12/11 11:09
 */
public class Authentication {

    //与对端建立连接与认证时使用的密钥
    private static final byte[] secretKey = "j8s1j9d0sa82@()U(@)$".getBytes(StandardCharsets.UTF_8);

    public static final Map<Integer, byte[]> tokenMap = new ConcurrentHashMap<>();

    private static AtomicInteger idIndex = new AtomicInteger();

    private static Properties properties;

    static {
        InputStream resourceAsStream = Authentication.class.getClassLoader().getResourceAsStream("user.properties");
        if (resourceAsStream == null) {
            System.out.println("未找到用户认证的配置文件");
            System.exit(0);
        }
        properties = new Properties();
        try {
            properties.load(resourceAsStream);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(0);
        }
    }

    public static void startAuthServer(String authPort) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try (ServerSocket authSocket = new ServerSocket(Integer.parseInt(authPort))) {
                System.out.println("SOCKS5 Proxy auth Server is running on port " + authPort);
                while (true) {
                    // 等待proximal连接
                    Socket clientSocket = authSocket.accept();
                    System.out.println("Accepted proximal connection from " +
                            clientSocket.getInetAddress() + ":" + clientSocket.getPort());
                    //实现权限认证
                    InputStream inputStream = clientSocket.getInputStream();
                    OutputStream outputStream = clientSocket.getOutputStream();
                    byte[] data = new byte[1024];
                    int bytesRead;
                    if ((bytesRead = inputStream.read(data)) != -1) {
                        byte[] validData = BytesUtil.splitBytes(data, 0, bytesRead);
                        boolean authResult = Authentication.proximalAuth(validData);
                        if (authResult) {
                            System.out.println("auth success");
                            int id = idIndex.incrementAndGet();
                            byte[] token = createToken();
                            tokenMap.put(id, token);
                            byte[] idBytes = BytesUtil.toBytesH(id);
                            // 0x00认证失败 0x01认证成功
                            byte[] outData = BytesUtil.concatBytes(new byte[]{0x01}, idBytes, token);
                            outputStream.write(outData);
                        } else {
                            System.out.println("auth fail!");
                            // 0x00认证失败 0x01认证成功
                            outputStream.write(new byte[]{(byte) 0x00});
                        }
                    }
                    clientSocket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public static byte[] createToken() {
        Random random = new Random();
        byte[] token = new byte[16];
        for (int i = 0; i < 4; i++) {
            int i1 = random.nextInt();
            byte[] bytes = BytesUtil.toBytesH(i1);
            token[i * 4] = bytes[0];
            token[i * 4 + 1] = bytes[1];
            token[i * 4 + 2] = bytes[2];
            token[i * 4 + 3] = bytes[3];
        }
        return token;
    }

    /**
     * proximal的连接认证
     *
     * @param data
     * @return
     * @throws IOException
     */
    public static boolean proximalAuth(byte[] data) throws IOException {
        Authentication.encryptDecrypt(data);
        ByteBuffer buffer = ByteBuffer.wrap(data);
        int version = buffer.get();     //版本，ocean1的值是0x01
        int userLen = buffer.getInt(); //用户名的长度
        byte[] username = new byte[userLen];
        buffer.get(username);
        int pwdLen = buffer.getInt();
        byte[] password = new byte[pwdLen];
        buffer.get(password);

        String user = new String(username, StandardCharsets.UTF_8);
        String configPwd = properties.getProperty(user);
        String pwd = new String(password, StandardCharsets.UTF_8);
        System.out.println("auth username:" + user + ", password:" + pwd);
        if (StringUtils.isEmpty(configPwd)) {
            System.out.println("no user：" + user);
            return false;
        }
        if (configPwd.equals(pwd)) {
            return true;
        } else {
            System.out.println("password error");
        }
        return false;
    }

    @Synchronized
    public static void encryptDecrypt(byte[] data) {
        for (int i = 0; i < data.length; i++) {
            data[i] ^= (secretKey[i % secretKey.length]);
        }
    }

}
