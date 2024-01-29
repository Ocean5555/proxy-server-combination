package com.ocean.proxy.server.distal.service;

import com.ocean.proxy.server.distal.util.BytesUtil;
import sun.awt.SunHints;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ProxyServer {

    private static ExecutorService executorService = Executors.newCachedThreadPool();;

    /**
     * 开启连接服务，客户端发起新连接的时候触发。
     *
     * @param connectSocket
     */
    public static void startConnectServer(ServerSocket connectSocket, Socket proximalAuthSocket) {
        Executors.newSingleThreadExecutor().execute(() -> {
            while (!connectSocket.isClosed()) {
                try {
                    //建立代理线程，等待客户端发起的代理连接
                    Socket proximalSocket = connectSocket.accept();
                    System.out.println("=======================================");
                    System.out.println("rise a connect from " +
                            proximalSocket.getInetAddress().toString() + ":" + proximalSocket.getPort());
                    createConnectThread(proximalSocket);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            System.out.println("proximalAuthSocket has closed!");
        });
        // Executors.newSingleThreadExecutor().execute(() -> {
        //     while (true) {
        //         try {
        //             Thread.sleep(3000);
        //         } catch (InterruptedException e) {
        //             e.printStackTrace();
        //         }
        //         try {
        //             proximalAuthSocket.sendUrgentData(0xFF);
        //         } catch (IOException e) {
        //             System.out.println("proximal socket is close, close the connect port!");
        //             try {
        //                 connectSocket.close();
        //             } catch (IOException e1) {
        //                 e1.printStackTrace();
        //             }
        //             return;
        //         }
        //     }
        //
        // });
    }


    /**
     * 创建处理新连接的线程
     *
     * @param proximalSocket
     * @throws IOException
     */
    private static void createConnectThread(Socket proximalSocket) {
        executorService.execute(() -> {
            try {
                InputStream proximalInput = proximalSocket.getInputStream();
                // 从客户端读取数据并发送到目标服务器
                int defaultLen = 1024;
                byte[] data = new byte[defaultLen];
                int bytesRead;
                //处理连接信息
                if ((bytesRead = proximalInput.read(data)) != -1) {
                    byte[] validData = BytesUtil.splitBytes(data, 0, bytesRead);
                    data = new byte[defaultLen];
                    Authentication.encryptDecrypt(validData);
                    ByteBuffer buffer = ByteBuffer.wrap(validData);
                    byte version = buffer.get();
                    int id = buffer.getInt();
                    if (Authentication.tokenMap.containsKey(id)) {
                        byte[] token = new byte[16];
                        buffer.get(token);
                        byte[] cacheToken = Authentication.tokenMap.get(id);
                        if (BytesUtil.toHexString(token).equals(BytesUtil.toHexString(cacheToken))) {
                            byte[] remaining = new byte[buffer.remaining()];
                            buffer.get(remaining);
                            System.out.println("token verification passed");
                            processConnectData(remaining, proximalSocket, token);
                        } else {
                            System.out.println("token verification fail!");
                            responseConnectFail(proximalSocket);
                        }
                    } else {
                        System.out.println("token verification fail!");
                        responseConnectFail(proximalSocket);
                    }
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

    /**
     * 处理客户端发起的与目标服务器的连接请求
     * 建立连接，proximal->distal, distal->targetServer
     *
     * @param data           建立连接的信息
     * @param proximalSocket
     */
    private static void processConnectData(byte[] data, Socket proximalSocket, byte[] token) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            //获取要连接的目标服务器地址和端口
            int addressLen = buffer.getInt();
            byte[] address = new byte[addressLen];
            buffer.get(address);
            String targetAddress = new String(address, StandardCharsets.UTF_8);
            int targetPort = buffer.getInt();
            System.out.println("start connect target " + targetAddress + ":" + targetPort);
            //与目标服务建立连接
            Socket targetSocket = new Socket(targetAddress, targetPort);
            System.out.println("success connected");
            //绑定proximal的连接与target的连接
            DataTransHandler.bindProximalAndTarget(proximalSocket, targetSocket, token);

            //返回结果数据，格式：发送的数据+状态+新端口+token
            byte[] status = new byte[]{0x01};
            byte[] responseData = BytesUtil.concatBytes(status, token);
            Authentication.encryptDecrypt(responseData);
            OutputStream proximalOutput = proximalSocket.getOutputStream();
            proximalOutput.write(responseData);
        } catch (Exception e) {
            e.printStackTrace();
            responseConnectFail(proximalSocket);
        }
    }

    private static void responseConnectFail(Socket proximalSocket) {
        try {
            //返回与目标服务连接失败的信息
            OutputStream proximalOutput = proximalSocket.getOutputStream();
            proximalOutput.write(0x00);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
