package com.ocean.proxy.server.proximal.handler;

import com.ocean.proxy.server.proximal.service.AuthToDistal;
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
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
     * 0：未连接，1：成功连接，2：连接后断开
     */
    private Integer targetConnected = 0;

    /**
     * 与distal连接后，使用的通道
     */
    private ChannelHandlerContext ctx;

    /**
     * 缓存从distal接收的数据，用来对完整数据解密（AES加密后的数据，必须拿到全部数据才能进行解密）
     */
    private byte[] cache = new byte[0];

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
        if (targetConnected == 0) {
            //第一次连接后接收数据
            //通过默认密码解密
            AuthToDistal.encryptDecrypt(data);
            ByteBuffer buffer = ByteBuffer.wrap(data);
            byte status = buffer.get();
            if (status == 1) {
                //成功
                log.info("connect info correct.");
                targetConnected = 1;
            } else {
                //失败
                log.info("fail connect distal, connect info error.");
                targetConnected = 2;
            }
        } else {
            //接收distal数据转发给client
            //通过token解密
            cache = BytesUtil.concatBytes(cache, data);
            ByteBuffer buffer = ByteBuffer.wrap(cache);
            int length = buffer.getInt();
            while (buffer.remaining() >= length) {
                //拿到一条完整加密数据
                byte[] encryptData = new byte[length];
                buffer.get(encryptData);
                //解密发送
                byte[] sendData = AuthToDistal.decryptData(encryptData);
                OutputStream outputStream = clientSocket.getOutputStream();
                try {
                    outputStream.write(sendData);
                } catch (SocketException e) {
                    log.info("client exception: " + e.getMessage());
                    ctx.close();
                    clientSocket.close();
                }
                if (buffer.remaining() > 4) {
                    length = buffer.getInt();
                }
            }
            if(buffer.remaining() == 0){
                cache = new byte[0];
            } else if (buffer.remaining() > 4) {
                byte[] lastData = new byte[buffer.remaining()];
                buffer.get(lastData);
                cache = BytesUtil.concatBytes(BytesUtil.toBytesH(length), lastData);
            }else{
                byte[] lastData = new byte[buffer.remaining()];
                buffer.get(lastData);
                cache = lastData;
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
                while (clientSocket == null) {
                    Thread.sleep(15);
                }
                if (clientSocket.isClosed()) {
                    return;
                }
                InputStream input = clientSocket.getInputStream();
                Channel distalChannel = ctx.channel();
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
                    e.printStackTrace();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }finally {
                if (ctx.channel().isOpen()) {
                    ctx.channel().close();
                }
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
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

}
