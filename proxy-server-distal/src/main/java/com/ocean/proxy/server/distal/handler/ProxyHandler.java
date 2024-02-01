package com.ocean.proxy.server.distal.handler;

import com.ocean.proxy.server.distal.service.TargetServer;
import com.ocean.proxy.server.distal.util.BytesUtil;
import com.ocean.proxy.server.distal.util.CipherUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>Description:</b>  <br/>
 * <b>@Author:</b> Ocean <br/>
 * <b>@DateTime:</b> 2024/1/29 14:52
 */
public class ProxyHandler extends ChannelInboundHandlerAdapter {

    private final Map<String, TargetHandler> channelMap = new ConcurrentHashMap<>();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf byteBuf = (ByteBuf) msg;
        byte[] data = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(data);
        Channel proximalChannel = ctx.channel();
        String channelId = proximalChannel.id().asLongText();
        if (!channelMap.containsKey(channelId)) {
            //第一次发过来数据，其中是连接的认证信息
            AuthenticationHandler.encryptDecrypt(data);
            ByteBuffer buffer = ByteBuffer.wrap(data);
            byte version = buffer.get();
            int id = buffer.getInt();
            if (AuthenticationHandler.tokenMap.containsKey(id)) {
                byte[] token = new byte[16];
                buffer.get(token);
                byte[] cacheToken = AuthenticationHandler.tokenMap.get(id);
                if (BytesUtil.toHexString(token).equals(BytesUtil.toHexString(cacheToken))) {
                    byte[] remaining = new byte[buffer.remaining()];
                    buffer.get(remaining);
                    System.out.println("token verification passed");
                    TargetHandler targetHandler = new TargetHandler(proximalChannel, new CipherUtil(token));
                    channelMap.put(channelId, targetHandler);
                    processConnectData(remaining, proximalChannel, targetHandler);
                } else {
                    System.out.println("token verification fail!");
                    System.out.println("receive token:" + BytesUtil.toHexString(token)
                            + ", cacheToken:" + BytesUtil.toHexString(cacheToken));
                    responseConnectFail(proximalChannel);
                }
            } else {
                System.out.println("token verification fail!");
                responseConnectFail(proximalChannel);
            }
            System.out.println("=======================================");
        } else {
            //转发数据
            TargetHandler targetHandler = channelMap.get(channelId);
            if (targetHandler != null) {
                boolean b = targetHandler.writeToTarget(data);
                if (!b) {
                    channelMap.remove(channelId);
                    ctx.channel().close();
                }
            }
        }

    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("=======================================");
        System.out.println("rise a connect from " + ctx.channel().remoteAddress());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        //发生异常，关闭通道
        System.out.println("proxy ctx occur error, close connect");
        cause.printStackTrace();
        String channelId = ctx.channel().id().asLongText();
        channelMap.remove(channelId);
        ctx.channel().close();
    }

    /**
     * 处理客户端发起的与目标服务器的连接请求
     * 建立连接，proximal->distal, distal->targetServer
     *
     * @param data            建立连接的信息
     * @param proximalChannel
     */
    private void processConnectData(byte[] data, Channel proximalChannel, TargetHandler targetHandler) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            //获取要连接的目标服务器地址和端口
            int addressLen = buffer.getInt();
            byte[] address = new byte[addressLen];
            buffer.get(address);
            String targetAddress = new String(address, StandardCharsets.UTF_8);
            int targetPort = buffer.getInt();
            //创建与target的连接
            TargetServer.createTargetConnect(targetAddress, targetPort, targetHandler);
            //返回结果数据，格式：状态
            byte[] status = new byte[]{0x01};
            AuthenticationHandler.encryptDecrypt(status);
            ByteBuf buf = Unpooled.buffer();
            buf.writeBytes(status);
            proximalChannel.writeAndFlush(buf);
            System.out.println("response success to proximal.");
        } catch (Exception e) {
            e.printStackTrace();
            responseConnectFail(proximalChannel);
            System.out.println("response fail to proximal.");
        }
    }

    private void responseConnectFail(Channel proximalChannel) {
        try {
            //返回与目标服务连接失败的信息
            ByteBuf buffer = Unpooled.buffer();
            buffer.writeBytes(new byte[]{0x00});
            proximalChannel.writeAndFlush(buffer);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
