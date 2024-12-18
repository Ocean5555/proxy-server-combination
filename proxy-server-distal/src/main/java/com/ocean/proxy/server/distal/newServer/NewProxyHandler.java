package com.ocean.proxy.server.distal.newServer;

import com.ocean.proxy.server.distal.handler.AuthenticationHandler;
import com.ocean.proxy.server.distal.util.BytesUtil;
import com.ocean.proxy.server.distal.util.CipherUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Description:
 *
 * @author Ocean
 * datetime: 2024/12/17 10:38
 */
@Slf4j
public class NewProxyHandler {

    private final Socket proximalSocket;
    private NewTargetHandler targetHandler;

    public NewProxyHandler(Socket proximalSocket) {
        this.proximalSocket = proximalSocket;
    }

    public void start() throws Exception {
        int bytesRead;
        InputStream proximalInputStream = proximalSocket.getInputStream();
        OutputStream proximalOutputStream = proximalSocket.getOutputStream();
        while (!proximalSocket.isClosed()) {
            if ((bytesRead = proximalInputStream.available()) != 0) {
                byte[] validData = new byte[bytesRead];
                int read = proximalInputStream.read(validData);
                if (read == -1) {
                    continue;
                }
                if (targetHandler == null) {
                    //第一次发过来数据，其中包含连接认证信息
                    AuthenticationHandler.encryptDecrypt(validData);
                    ByteBuffer buffer = ByteBuffer.wrap(validData);
                    byte version = buffer.get();
                    int id = buffer.getInt();
                    if (AuthenticationHandler.tokenMap.containsKey(id)) {
                        byte[] token = new byte[16];
                        buffer.get(token);
                        byte[] cacheToken = AuthenticationHandler.tokenMap.get(id);
                        if (BytesUtil.toHexString(token).equals(BytesUtil.toHexString(cacheToken))) {
                            log.info("token verification passed");
                            //获取要连接的目标服务器地址和端口
                            int addressLen = buffer.getInt();
                            byte[] address = new byte[addressLen];
                            buffer.get(address);
                            String targetAddress = new String(address, StandardCharsets.UTF_8);
                            int targetPort = buffer.getInt();
                            try {
                                //创建与target的连接
                                targetHandler = new NewTargetHandler(proximalSocket, new CipherUtil(token), targetAddress, targetPort);
                                responseConnectSuccess(proximalOutputStream);
                            } catch (Exception e) {
                                log.error("connect to target("+targetAddress+":"+targetPort+") fail", e);
                                responseConnectFail(proximalOutputStream);
                            }
                        } else {
                            log.info("token verification fail! token is wrong");
                            log.info("receive token:" + BytesUtil.toHexString(token)
                                    + ", cacheToken:" + BytesUtil.toHexString(cacheToken));
                            responseAuthFail(proximalOutputStream);
                        }
                    } else {
                        log.info("token verification fail! not exist id:" + id);
                        responseAuthFail(proximalOutputStream);
                    }
                    log.info("=======================================");
                } else {
                    //转发数据
                    boolean b = targetHandler.writeToTarget(validData);
                    if (!b) {
                        proximalSocket.close();
                    }
                }
            } else {
                Thread.sleep(20);
            }
        }
        if (targetHandler != null) {
            targetHandler.closeConnect();
        }
    }


    //返回与目标服务连接成功的信息
    private void responseConnectSuccess(OutputStream outputStream) throws IOException {
        byte[] status = new byte[]{0x01};
        responseData(outputStream, status);
        log.info("response success to proximal.");
    }

    //返回与目标服务连接失败的信息
    private void responseConnectFail(OutputStream outputStream) throws IOException {
        byte[] status = new byte[]{0x00};
        responseData(outputStream, status);
        log.info("response fail to proximal.");
    }

    //返回认证失败信息
    private void responseAuthFail(OutputStream outputStream) throws IOException {
        byte[] status = new byte[]{0x02};
        responseData(outputStream, status);
    }

    private void responseData(OutputStream outputStream, byte[] status) throws IOException {
        ByteBuf buffer = Unpooled.buffer();
        byte[] randomData = RandomUtils.nextBytes(RandomUtils.nextInt(5, 35));
        byte[] sendData = BytesUtil.concatBytes(status, randomData);
        AuthenticationHandler.encryptDecrypt(sendData);
        buffer.writeBytes(sendData);
        try {

        } catch (Exception e) {

        }
        outputStream.write(buffer.array());
    }

    private void closeTargetConnect() {
        if (targetHandler != null) {
            targetHandler.closeConnect();
        }
    }
}
