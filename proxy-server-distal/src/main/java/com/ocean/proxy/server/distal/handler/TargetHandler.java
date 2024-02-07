package com.ocean.proxy.server.distal.handler;

import com.ocean.proxy.server.distal.util.CipherUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;

/**
 * <b>Description:</b>  <br/>
 * <b>@Author:</b> Ocean <br/>
 * <b>@DateTime:</b> 2023/12/8 18:06
 */
public class TargetHandler extends ChannelInboundHandlerAdapter {

    private final Channel proximalChannel;

    private Channel targetChannel;

    private final CipherUtil cipherUtil;

    private boolean connected = false;

    public boolean getConnected() {
        return connected;
    }

    public TargetHandler(Channel proximalChannel, CipherUtil cipherUtil) {
        this.proximalChannel = proximalChannel;
        this.cipherUtil = cipherUtil;
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
        //获取target发送过来的消息
        ByteBuf byteBuf = (ByteBuf) msg;
        byte[] data = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(data);
        //加密
        data = cipherUtil.encryptData(data);
        //发给proximal
        ByteBuf buf = Unpooled.buffer();
        buf.writeBytes(data);
        proximalChannel.writeAndFlush(buf);

        // if (data.length <= 1024) {
        //     //加密
        //     data = cipherUtil.encryptData(data);
        //     //发给proximal
        //     ByteBuf buf = Unpooled.buffer();
        //     buf.writeBytes(data);
        //     proximalChannel.writeAndFlush(buf);
        // } else {
        //     //数据超过默认缓存区1024长度，分段加密传输
        //     int offset = 0;
        //     while (offset < data.length) {
        //         int len = data.length - offset;
        //         if (len >= 1024) {
        //             len = 1024;
        //         }
        //         byte[] bytes = BytesUtil.splitBytes(data, offset, len);
        //         offset += len;
        //         bytes = cipherUtil.encryptData(bytes);
        //         //发给proximal
        //         ByteBuf buf = Unpooled.buffer();
        //         buf.writeBytes(bytes);
        //         proximalChannel.writeAndFlush(buf);
        //     }
        // }
    }

    public boolean writeToTarget(byte[] data) throws Exception {
        if (targetChannel != null && targetChannel.isOpen()) {
            //解密
            data = cipherUtil.decryptData(data);
            ByteBuf buf = Unpooled.buffer();
            buf.writeBytes(data);
            targetChannel.writeAndFlush(buf);
            return true;
        } else {
            System.out.println("target channel is closed!");
            return false;
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
        System.out.println("connect target active!");
        targetChannel = ctx.channel();
        connected = true;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        //发生异常，关闭通道
        System.out.println("target ctx occur error, close connect");
        if(!cause.getMessage().contains("Connection reset by peer")){
            cause.printStackTrace();
        }
        ctx.close();
    }

}
