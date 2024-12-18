package com.ocean.proxy.server.distal.newServer;

import com.ocean.proxy.server.distal.util.BytesUtil;
import com.ocean.proxy.server.distal.util.CipherUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Description:
 *
 * @author Ocean
 * datetime: 2024/12/17 13:56
 */
@Slf4j
public class NewTargetHandler {

    private static final ExecutorService executorService = Executors.newCachedThreadPool();
    private final CipherUtil cipherUtil;

    private final Socket proximalSocket;

    private final Socket targetSocket;

    private byte[] cache = new byte[0];

    public NewTargetHandler(Socket proximalSocket, CipherUtil cipherUtil, String targetAddress, Integer targetPort) throws Exception {
        this.proximalSocket = proximalSocket;
        this.cipherUtil = cipherUtil;
        if(targetAddress.contains(":")){
            //IPV6
            InetAddress inetAddress = InetAddress.getByName(targetAddress);
            targetSocket = new Socket(inetAddress, targetPort);
        }else{
            targetSocket = new Socket(targetAddress, targetPort);
        }
        log.info("connect target({}) success!", targetAddress + ":" + targetPort);
        //创建一个线程从target读取数据
        executorService.execute(() -> {
            try {
                readFromTarget(targetSocket);
            } catch (Exception e) {
                log.error("", e);
            }
        });
    }

    private void readFromTarget(Socket targetSocket) throws Exception {
        InputStream targetInputStream = targetSocket.getInputStream();
        OutputStream proximalOutputStream = proximalSocket.getOutputStream();
        //获取target发送过来的消息
        int bytesRead;
        while (!targetSocket.isClosed()) {
            if ((bytesRead = targetInputStream.available()) != 0) {
                byte[] validData = new byte[bytesRead];
                int read = targetInputStream.read(validData);
                if (read == -1) {
                    continue;
                }
                //加密
                validData = cipherUtil.encryptDataAes(validData);
                byte[] length = BytesUtil.toBytesH(validData.length);
                //发给proximal
                proximalOutputStream.write(BytesUtil.concatBytes(length, validData));
            } else {
                Thread.sleep(20);
            }
        }
    }

    public boolean writeToTarget(byte[] data) throws Exception {
        if (!targetSocket.isClosed()) {
            cache = BytesUtil.concatBytes(cache, data);
            if (cache.length > 4) {
                ByteBuffer buffer = ByteBuffer.wrap(cache);
                int length = buffer.getInt();
                while (buffer.remaining() >= length) {
                    //拿到一条完整加密数据
                    byte[] encryptData = new byte[length];
                    buffer.get(encryptData);
                    //解密发送
                    byte[] sendData = cipherUtil.decryptDataAes(encryptData);
                    targetSocket.getOutputStream().write(sendData);
                    if (buffer.remaining() > 4) {
                        length = buffer.getInt();
                    }
                }
                if (buffer.remaining() <= 4) {
                    byte[] lastData = new byte[buffer.remaining()];
                    buffer.get(lastData);
                    cache = lastData;
                } else {
                    byte[] lastData = new byte[buffer.remaining()];
                    buffer.get(lastData);
                    cache = BytesUtil.concatBytes(BytesUtil.toBytesH(length), lastData);
                }
            }
            return true;
        } else {
            log.info("target channel is closed!");
            return false;
        }
    }

    public void closeConnect() {
        if (targetSocket != null) {
            try {
                targetSocket.close();
            } catch (IOException ignore) {
            }
        }
    }
}
