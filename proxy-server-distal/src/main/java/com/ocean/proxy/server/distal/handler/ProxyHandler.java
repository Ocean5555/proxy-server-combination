package com.ocean.proxy.server.distal.handler;

import com.ocean.proxy.server.distal.service.TargetServer;
import com.ocean.proxy.server.distal.util.BytesUtil;
import com.ocean.proxy.server.distal.util.CipherUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>Description:</b>  <br/>
 * <b>@Author:</b> Ocean <br/>
 * <b>@DateTime:</b> 2024/1/29 14:52
 */
@Slf4j
public class ProxyHandler extends ChannelInboundHandlerAdapter {

    private final Map<String, TargetHandler> channelMap = new ConcurrentHashMap<>();

    private TargetHandler targetHandler;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf byteBuf = (ByteBuf) msg;
        byte[] data = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(data);
        byteBuf.release();
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
                    log.info("token verification passed");
                    targetHandler = new TargetHandler(proximalChannel, new CipherUtil(token));
                    channelMap.put(channelId, targetHandler);
                    processConnectData(remaining, proximalChannel, targetHandler);
                } else {
                    log.info("token verification fail! token is wrong");
                    log.info("receive token:" + BytesUtil.toHexString(token)
                            + ", cacheToken:" + BytesUtil.toHexString(cacheToken));
                    responseAuthFail(proximalChannel);
                }
            } else {
                log.info("token verification fail! not exist id:" + id);
                responseAuthFail(proximalChannel);
            }
            log.info("=======================================");
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
        log.info("=======================================");
        log.info("rise a connect from " + ctx.channel().remoteAddress());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        //发生异常，关闭通道
        log.error("proxy ctx occur error, close connect. proxy connection count:" + channelMap.keySet().size());
        String channelId = ctx.channel().id().asLongText();
        channelMap.remove(channelId);
        if (!cause.getMessage().contains("Connection reset by peer")) {
            cause.printStackTrace();
        }
        ctx.channel().close();
        closeTargetConnect();
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
            int times = 20;
            while (!targetHandler.getConnected()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                times--;
                if (times <= 0) {
                    throw new RuntimeException("target connect timeout！");
                }
            }
            responseConnectSuccess(proximalChannel);
        } catch (Exception e) {
            e.printStackTrace();
            responseConnectFail(proximalChannel);
        }
    }

    //返回与目标服务连接成功的信息
    private void responseConnectSuccess(Channel proximalChannel) {
        byte[] status = new byte[]{0x01};
        responseData(proximalChannel, status);
        log.info("response success to proximal.");
    }

    //返回与目标服务连接失败的信息
    private void responseConnectFail(Channel proximalChannel) {
        byte[] status = new byte[]{0x00};
        responseData(proximalChannel, status);
        log.info("response fail to proximal.");
    }

    //返回认证失败信息
    private void responseAuthFail(Channel proximalChannel) {
        byte[] status = new byte[]{0x02};
        responseData(proximalChannel, status);
    }

    private void responseData(Channel proximalChannel, byte[] status){
        ByteBuf buffer = Unpooled.buffer();
        byte[] randomData = RandomUtils.nextBytes(RandomUtils.nextInt(5, 35));
        byte[] sendData = BytesUtil.concatBytes(status, randomData);
        AuthenticationHandler.encryptDecrypt(sendData);
        buffer.writeBytes(sendData);
        proximalChannel.writeAndFlush(buffer);
    }

    private void closeTargetConnect(){
        if (targetHandler != null) {
            targetHandler.closeConnect();
        }
    }
}
