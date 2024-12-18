package com.ocean.proxy.server.proximal.handler;

import com.ocean.proxy.server.proximal.service.AuthToDistal;
import com.ocean.proxy.server.proximal.service.ConfigReader;
import com.ocean.proxy.server.proximal.util.BytesUtil;
import com.ocean.proxy.server.proximal.util.CustomThreadFactory;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <b>Description:</b>  <br/>
 * <b>@Author:</b> Ocean <br/>
 * <b>@DateTime:</b> 2023/12/11 11:44
 */
@EqualsAndHashCode(callSuper = false)
@Data
@Slf4j
public class DistalHandler extends ChannelInboundHandlerAdapter {

    /**
     * 与客户端交互的线程池。
     * 每个线程负责对一个客户端进行数据交互
     */
    private static final ExecutorService executorService = Executors.newCachedThreadPool(new CustomThreadFactory("clientRead-"));

    /**
     * 客户端连接上来后使用的socket
     */
    private Socket clientSocket;

    /**
     * 是否已连接
     * 0：未连接，1：成功连接，2：连接后断开
     */
    private Integer effective = 0;

    /**
     * 提交给distal的target地址和端口，是否由distal成功连接
     * -1:未连接， 0：连接失败，1：成功连接，2：认证失败
     */
    private Integer targetConnectStatus = -1;

    /**
     * 与distal连接后，使用的通道
     */
    private ChannelHandlerContext ctx;

    /**
     * 缓存从distal接收的数据，用来对完整数据解密（AES加密后的数据，必须拿到全部数据才能进行解密）
     */
    private byte[] cache = new byte[0];

    private byte[] preData;

    /**
     * 收到distal发来的数据
     *
     * @param ctx
     * @param msg
     * @throws Exception
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf byteBuf = (ByteBuf) msg;
        byte[] data = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(data);
        byteBuf.release();
        //获取distal发送过来的消息
        if (targetConnectStatus == -1) {
            //第一次连接后接收数据
            //通过默认密码解密
            AuthToDistal.encryptDecrypt(data);
            ByteBuffer buffer = ByteBuffer.wrap(data);
            int status = buffer.get();
            targetConnectStatus = status;
            if (status == 1) {
                //成功
                log.info("connect info correct.");
            } else if (status == 2){
                //token认证失败，可能distal重启了
                log.error("fail auth to distal.***************************************************************");
            } else if (status == 0) {
                throw new RuntimeException("distal connect target fail!");
            }else {
                throw new RuntimeException("status code error! " + status);
            }
        } else {
            //接收distal数据转发给client
            cache = BytesUtil.concatBytes(cache, data);
            if (cache.length > 4) {
                ByteBuffer buffer = ByteBuffer.wrap(cache);
                int length = buffer.getInt();
                while (buffer.remaining() >= length) {
                    //拿到一条完整加密数据
                    byte[] encryptData = new byte[length];
                    buffer.get(encryptData);
                    //解密发送
                    byte[] sendData = AuthToDistal.decryptData(encryptData);
                    clientSocket.getOutputStream().write(sendData);
                    if (buffer.remaining() > 4) {
                        length = buffer.getInt();
                    }
                }
                if (buffer.remaining() <= 4) {
                    byte[] lastData = new byte[buffer.remaining()];
                    buffer.get(lastData);
                    cache = lastData;
                }else{
                    byte[] lastData = new byte[buffer.remaining()];
                    buffer.get(lastData);
                    cache = BytesUtil.concatBytes(BytesUtil.toBytesH(length), lastData);
                }
            }
        }
    }

    /**
     * 与distal建立连接完成后触发
     *
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("distal connect active!");
        this.ctx = ctx;
        //开启线程，接收client数据转发给distal
        executorService.execute(() -> {
            try {
                while (ctx.channel().isOpen() && clientSocket == null) {
                    Thread.sleep(15);
                }
                if (!ctx.channel().isOpen() || clientSocket.isClosed()) {
                    return;
                }
                InputStream input = clientSocket.getInputStream();
                Channel distalChannel = ctx.channel();
                if (preData != null) {
                    //通过token加密
                    preData = AuthToDistal.encryptData(preData);
                    byte[] length = BytesUtil.toBytesH(preData.length);
                    ByteBuf buf = Unpooled.buffer();
                    buf.writeBytes(length);
                    buf.writeBytes(preData);
                    distalChannel.writeAndFlush(buf);
                    preData = null;
                }
                //源端与目标端数据传输
                int bytesRead;
                while (!clientSocket.isClosed()) {
                    if ((bytesRead = input.available()) != 0) {
                        byte[] validData = new byte[bytesRead];
                        int read = input.read(validData);
                        if (read == -1) {
                            continue;
                        }
                        if (distalChannel.isOpen()) {
                            //通过token加密
                            validData = AuthToDistal.encryptData(validData);
                            byte[] length = BytesUtil.toBytesH(validData.length);
                            ByteBuf buf = Unpooled.buffer();
                            buf.writeBytes(length);
                            buf.writeBytes(validData);
                            distalChannel.writeAndFlush(buf);
                        }
                    } else {
                        Thread.sleep(50);
                    }
                }
            } catch (SocketException e) {
                if ("Connection reset".equals(e.getMessage())) {
                    // 处理Connection Reset状态
                    log.info("Connection reset by peer");
                } else {
                    // 处理其他SocketException
                    log.error("", e);
                }
            } catch (Exception e) {
                log.error("", e);
            }finally {
                if (ctx.channel().isOpen()) {
                    ctx.channel().close();
                }
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    log.error("", e);
                }
            }
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        //发生异常，关闭通道
        log.info("distal ctx occur error, close connect");
        cause.printStackTrace();
        ctx.close();
    }

    public void close(){
        ctx.close();
    }


    public void waitConnectTarget(String targetAddress, Integer targetPort, ConfigReader configReader){
        int times = 50;
        while (getTargetConnectStatus() == -1) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            times--;
            if (times <= 0) {
                log.error("connect timeout！"+ targetAddress + ":" + targetPort);
                close();
                throw new RuntimeException("connect timeout！"+ targetAddress + ":" + targetPort);
            }
        }
        if (getTargetConnectStatus() == 2) {
            boolean b = false;
            try {
                b = AuthToDistal.distalAuth(configReader);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (!b) {
                log.info("auth fail, close this program");
                System.exit(0);
            }
        }
    }

    public void useActive(Socket clientSocket, String targetAddress, Integer targetPort) {
        setClientSocket(clientSocket);
        //发送新建连接的数据给distal
        byte[] version = new byte[]{0x01};
        byte[] addressBytes = targetAddress.getBytes(StandardCharsets.UTF_8);
        byte[] addressLen = BytesUtil.toBytesH(addressBytes.length);
        byte[] portBytes = BytesUtil.toBytesH(targetPort);
        byte[] id = AuthToDistal.getId();
        byte[] token = AuthToDistal.getToken();
        byte[] data = BytesUtil.concatBytes(version, id, token, addressLen, addressBytes, portBytes);
        byte[] randomData = RandomUtils.nextBytes(RandomUtils.nextInt(11, 99));
        byte[] sendData = BytesUtil.concatBytes(data, randomData);
        //通过默认密码加密
        AuthToDistal.encryptDecrypt(sendData);
        Channel distalChannel = ctx.channel();
        if (distalChannel.isOpen()) {
            ByteBuf buf = Unpooled.buffer();
            buf.writeBytes(sendData);
            distalChannel.writeAndFlush(buf);
        }
        log.info("send connect info!");
    }

}
