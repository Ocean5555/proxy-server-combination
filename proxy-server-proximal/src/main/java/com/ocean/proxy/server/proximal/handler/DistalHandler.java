package com.ocean.proxy.server.proximal.handler;

import com.ocean.proxy.server.proximal.service.AuthToDistal;
import com.ocean.proxy.server.proximal.util.BytesUtil;
import com.ocean.proxy.server.proximal.util.CustomThreadFactory;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

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
public class DistalHandler extends ChannelInboundHandlerAdapter {

    private static final ExecutorService executorService = Executors.newCachedThreadPool(new CustomThreadFactory("clientReadThread-"));;

    private Socket clientSocket;

    private Boolean targetConnected;

    private String targetAddress;

    private Integer targetPort;

    public Boolean getTargetConnected() {
        return targetConnected;
    }

    public DistalHandler(Socket clientSocket, String targetAddress, Integer targetPort) {
        this.clientSocket = clientSocket;
        this.targetAddress = targetAddress;
        this.targetPort = targetPort;
    }

    /**
     * 收到数据
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
        //获取distal发送过来的消息
        if (targetConnected == null) {
            //第一次连接后接收数据
            //通过默认密码解密
            AuthToDistal.encryptDecrypt(data);
            ByteBuffer buffer = ByteBuffer.wrap(data);
            byte status = buffer.get();
            if (status == 1) {
                //成功
                System.out.println("connect info correct.");
                targetConnected = true;
            } else {
                //失败
                System.out.println("fail connect distal, connect info error.");
                targetConnected = false;
            }
        } else {
            //接收distal数据转发给client
            //通过token解密
            data = AuthToDistal.decryptData(data);
            OutputStream outputStream = clientSocket.getOutputStream();
            outputStream.write(data);
        }
    }

    /**
     * 连接完成
     *
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("distal connect active!");
        //开启线程，接收client数据转发给distal
        executorService.execute(() -> {
            try {
                InputStream input = clientSocket.getInputStream();
                Channel distalChannel = ctx.channel();
                //源端与目标端数据传输
                int defaultLen = 1024;
                byte[] buffer = new byte[defaultLen];
                int bytesRead;
                while (!clientSocket.isClosed() && distalChannel.isOpen()) {
                    if ((bytesRead = input.read(buffer)) != -1) {
                        byte[] validData = BytesUtil.splitBytes(buffer, 0, bytesRead);
                        buffer = new byte[defaultLen];
                        //通过token加密
                        validData = AuthToDistal.encryptData(validData);
                        ByteBuf buf = Unpooled.buffer();
                        buf.writeBytes(validData);
                        distalChannel.writeAndFlush(buf);
                    } else {
                        Thread.sleep(10);
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
        sendTargetConnectData(ctx, targetAddress, targetPort);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        //发生异常，关闭通道
        System.out.println("target ctx occur error, close connect");
        cause.printStackTrace();
        ctx.close();
    }

    private void sendTargetConnectData(ChannelHandlerContext ctx, String targetAddress, Integer targetPort){
        //发送新建连接的数据给distal
        byte[] version = new byte[]{0x01};
        byte[] addressBytes = targetAddress.getBytes(StandardCharsets.UTF_8);
        byte[] addressLen = BytesUtil.toBytesH(addressBytes.length);
        byte[] portBytes = BytesUtil.toBytesH(targetPort);
        byte[] id = AuthToDistal.getId();
        byte[] token = AuthToDistal.getToken();
        byte[] data = BytesUtil.concatBytes(version, id, token, addressLen, addressBytes, portBytes);
        //通过默认密码加密
        AuthToDistal.encryptDecrypt(data);
        Channel distalChannel = ctx.channel();
        if (distalChannel.isOpen()) {
            ByteBuf buf = Unpooled.buffer();
            buf.writeBytes(data);
            distalChannel.writeAndFlush(buf);
            System.out.println("send connect info!");
        }
    }
}
