package com.ocean.proxy.server.proximal.service;

import com.ocean.proxy.server.proximal.handler.DistalHandler;
import com.ocean.proxy.server.proximal.util.BytesUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

@Slf4j
public class Socks5ProxyServer {

    public static void handleClient(Socket clientSocket) {
        try {
            // 实现 SOCKS 握手协商和建立连接的逻辑
            InputStream input = clientSocket.getInputStream();
            OutputStream output = clientSocket.getOutputStream();
            int methodsCount = input.read();    //指示其后的 METHOD 字段所占的字节数
            byte[] methods = new byte[methodsCount];   //methods表示客户端使用的认知方式，0x00表示不认证，0x03表示用户名密码认证
            input.read(methods);
            String s = BytesUtil.toHexString(methods);
            log.info("client auth type: 0x" + s);
            // 这里假设支持无需认证的方法，即0x00
            output.write(new byte[]{(byte) 0x05, (byte) 0x00});

            // 这部分逻辑需要根据 SOCKS5 协议规范实现
            handleConnectionRequest(clientSocket);
        } catch (Exception e) {
            e.printStackTrace();
            try {
                clientSocket.close();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }


    /**
     * 建立连接，客户端->代理服务器，代理服务器->目标服务
     * 客户端向代理服务器发起正式请求以指示所要访问的目标进程的地址, 端口
     *
     * @param clientSocket
     * @throws IOException
     */
    private static void handleConnectionRequest(Socket clientSocket) throws IOException {
        InputStream input = clientSocket.getInputStream();
        OutputStream output = clientSocket.getOutputStream();
        int version = input.read(); //版本，socks5的值是0x05
        int cmd = input.read(); //共有 3 个取值, 分别为 0x01 (CONNECT), 0x02 (BIND), 0x03 (UDP ASSOCIATE)
        int rsv = input.read(); // 固定为 0x00
        int addressType = input.read();
        // 目标地址类型，IPv4地址为0x01，IPv6地址为0x04，域名地址为0x03
        if (addressType == 0x01) {
            byte[] ipv4 = new byte[4];
            input.read(ipv4);
            String targetAddress = bytesToIpAddress(ipv4);
            int targetPort = input.read() << 8 | input.read();
            log.info("target:" + targetAddress + ":" + targetPort);
            try {
                DistalHandler distalHandler = new DistalHandler(clientSocket, targetAddress, targetPort);
                if (cmd == 0x01) {
                    DistalServer.createDistalConnect(distalHandler);
                    sendConnectionResponse(output, (byte) 0x00, ipv4, targetPort);
                } else if (cmd == 0x03) {
                    DistalServer.createDistalConnect(distalHandler);
                    handleUdpAssociateRequest(output);
                } else {
                    log.info("not support cmd!");
                    throw new RuntimeException("not support cmd");
                }
            } catch (Exception e) {
                sendConnectionResponse(output, (byte) 0x01, ipv4, targetPort);
                throw new RuntimeException("连接目标服务失败。", e);
            }
        } else if (addressType == 0x03) {
            // 域名地址
            int domainLength = input.read();
            byte[] domainBytes = new byte[domainLength];
            input.read(domainBytes);
            String targetDomain = new String(domainBytes);
            int targetPort = input.read() << 8 | input.read();
            log.info("target:" + targetDomain + ":" + targetPort);
            // 在实际应用中，可以根据 targetDomain 和 targetPort 与目标服务器建立连接
            try {
                DistalHandler distalHandler = new DistalHandler(clientSocket, targetDomain, targetPort);
                if (cmd == 0x01) {
                    // 发送连接成功的响应
                    DistalServer.createDistalConnect(distalHandler);
                    sendConnectionResponse(output, (byte) 0x00, targetDomain, targetPort);
                } else if (cmd == 0x03) {
                    DistalServer.createDistalConnect(distalHandler);
                    handleUdpAssociateRequest(output);
                } else {
                    log.info("not support cmd!");
                    throw new RuntimeException("not support cmd");
                }
            } catch (Exception e) {
                sendConnectionResponse(output, (byte) 0x01, targetDomain, targetPort);
                throw new RuntimeException("连接目标服务失败。", e);
            }
        } else {
            // 不支持的地址类型
            throw new RuntimeException("not support address type!");
        }
    }

    private static String bytesToIpAddress(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%d.", (b & 0xFF)));
        }
        return result.deleteCharAt(result.length() - 1).toString();
    }

    private static void handleUdpAssociateRequest(OutputStream output) throws IOException {
        // 假设监听 UDP 请求的端口为 5000
        int udpPort = 5000;
        output.write(new byte[]{(byte) 0x05, (byte) 0x00, (byte) 0x00, (byte) 0x03, (byte) 127, (byte) 0, (byte) 0, (byte) 1, (byte) (udpPort >> 8), (byte) udpPort});
    }

    /**
     * VER 字段占 1 字节, 表征协议版本, 固定为 0x05
     * REP 字段占 1 字节, 可以理解为状态码, 它的值表征了此次连接的状态:
     * 0x00 连接成功
     * 0x01 代理服务器出错
     * 0x02 连接不允许
     * 0x03 网络不可达
     * 0x04 主机不可达
     * 0x05 连接被拒绝
     * 0x06 TTL 到期
     * 0x07 命令 (CMD) 不支持
     * 0x08 地址类型不支持
     * 0x09 ~ 0xFF 目前没有分配
     * RSV 字段占 1 字节, 为保留字段, 固定为 0x00
     * ATYP 字段与请求的 ATYP 字段含义相同
     * BND.ADDR 与 BND.PORT 的含义随请求中的 CMD 的不同而不同, 下面我们依次展开讨论 3 种 CMD: CONNECT, BIND 以及 UDP ASSOCIATE
     *
     * @param status
     * @param ipv4
     * @param targetPort
     * @return
     */
    private static void sendConnectionResponse(OutputStream output, byte status, byte[] ipv4, int targetPort) throws IOException {
        // 发送连接响应
        output.write(new byte[]{(byte) 0x05, status, (byte) 0x00, (byte) 0x01, ipv4[0], ipv4[1], ipv4[2], ipv4[3], (byte) (targetPort >> 8), (byte) targetPort});
    }

    private static void sendConnectionResponse(OutputStream output, byte status, String targetDomain, int targetPort) throws IOException {
        // 发送连接响应
        byte[] domainBytes = targetDomain.getBytes();
        int domainLength = domainBytes.length;
        output.write(new byte[]{(byte) 0x05, status, (byte) 0x00, (byte) 0x03, (byte) domainLength});
        output.write(domainBytes);
        output.write(new byte[]{(byte) (targetPort >> 8), (byte) targetPort});
    }
}
