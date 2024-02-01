package com.ocean.proxy.server.proximal.handler;

import com.ocean.proxy.server.proximal.service.AuthToDistal;
import com.ocean.proxy.server.proximal.util.BytesUtil;
import com.ocean.proxy.server.proximal.util.CipherUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import javax.xml.ws.BindingType;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;

/**
 * <b>Description:</b>  <br/>
 * <b>@Author:</b> Ocean <br/>
 * <b>@DateTime:</b> 2023/12/11 11:44
 */
public class DistalHandler extends ChannelInboundHandlerAdapter {

    private Socket clientSocket;

    private Boolean connected;

    public Boolean getConnected() {
        return connected;
    }

    public DistalHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
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
        if (connected == null) {
            //第一次连接后接收数据
            //通过默认密码解密
            AuthToDistal.encryptDecrypt(data);
            ByteBuffer buffer = ByteBuffer.wrap(data);
            byte status = buffer.get();
            if (status == 1) {
                //成功
                System.out.println("success connect distal, status correct.");
                connected = true;
            } else {
                //失败
                System.out.println("fail connect distal, status error.");
                connected = false;
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
        //开启线程，接收client数据转发给distal
        Executors.newSingleThreadScheduledExecutor().execute(() -> {
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
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        //发生异常，关闭通道
        System.out.println("target ctx occur error, close connect");
        cause.printStackTrace();
        ctx.close();
    }

}
