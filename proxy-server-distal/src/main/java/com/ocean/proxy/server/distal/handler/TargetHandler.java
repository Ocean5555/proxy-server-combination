package com.ocean.proxy.server.distal.handler;

import com.ocean.proxy.server.distal.util.BytesUtil;
import com.ocean.proxy.server.distal.util.CipherUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;

/**
 * <b>Description:</b>  <br/>
 * <b>@Author:</b> Ocean <br/>
 * <b>@DateTime:</b> 2023/12/8 18:06
 */
@Slf4j
public class TargetHandler extends ChannelInboundHandlerAdapter {

    private final Channel proximalChannel;

    private Channel targetChannel;

    private final CipherUtil cipherUtil;

    private boolean connected = false;

    private byte[] cache = new byte[0];

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
        byteBuf.release();
        //加密
        data = cipherUtil.encryptDataAes(data);
        byte[] length = BytesUtil.toBytesH(data.length);
        //发给proximal
        ByteBuf buf = Unpooled.buffer();
        buf.writeBytes(length);
        buf.writeBytes(data);
        proximalChannel.writeAndFlush(buf);
    }

    public boolean writeToTarget(byte[] data) throws Exception {
        if (targetChannel != null && targetChannel.isOpen()) {
            cache = BytesUtil.concatBytes(cache, data);
            ByteBuffer buffer = ByteBuffer.wrap(cache);
            int length = buffer.getInt();
            while (buffer.remaining() >= length) {
                //拿到一条完整加密数据
                byte[] encryptData = new byte[length];
                buffer.get(encryptData);
                //解密发送
                byte[] sendData = cipherUtil.decryptDataAes(encryptData);
                ByteBuf buf = Unpooled.buffer();
                buf.writeBytes(sendData);
                targetChannel.writeAndFlush(buf);
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
            return true;
        } else {
            log.info("target channel is closed!");
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
        log.info("connect target active!");
        targetChannel = ctx.channel();
        connected = true;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        //发生异常，关闭通道
        log.error("target ctx occur error, close connect");
        if(!cause.getMessage().contains("Connection reset by peer")){
            cause.printStackTrace();
        }
        ctx.close();
        if (proximalChannel.isOpen()) {
            proximalChannel.close();
        }
    }

    public void closeConnect(){
        if (targetChannel.isOpen()) {
            targetChannel.close();
        }
    }

}
