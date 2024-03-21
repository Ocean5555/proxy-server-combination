package com.ocean.proxy.server.distal.handler;

import com.ocean.proxy.server.distal.util.BytesUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.Synchronized;
import org.apache.commons.lang3.BitField;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <b>Description:</b>  <br/>
 * <b>@Author:</b> Ocean <br/>
 * <b>@DateTime:</b> 2023/12/11 11:09
 */
public class AuthenticationHandler extends ChannelInboundHandlerAdapter {

    //与对端建立连接与认证时使用的密钥
    private static final byte[] secretKey = "j8s1j9d0sa82@()U(@)$".getBytes(StandardCharsets.UTF_8);

    public static final Map<Integer, byte[]> tokenMap = new ConcurrentHashMap<>();

    private static final Properties userProperties;

    static {
        InputStream resourceAsStream = AuthenticationHandler.class.getClassLoader().getResourceAsStream("user.properties");
        if (resourceAsStream == null) {
            System.out.println("未找到用户认证的配置文件");
            System.exit(0);
        }
        userProperties = new Properties();
        try {
            userProperties.load(resourceAsStream);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(0);
        }
    }

    private final Integer proxyPort;

    public AuthenticationHandler(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        //获取客户端发送过来的消息
        ByteBuf byteBuf = (ByteBuf) msg;
        System.out.println("Accepted proximal auth from " +
                ctx.channel().remoteAddress());
        byte[] data = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(data);
        byteBuf.release();
        encryptDecrypt(data);
        boolean authResult = proximalAuth(data);
        ByteBuf buffer = Unpooled.buffer();
        if (authResult) {
            System.out.println("auth success");
            byte[] idBytes = BytesUtil.hexToBytes(ctx.channel().id().asShortText());
            int id = (int) (BytesUtil.toNumberH(idBytes));
            byte[] token = createToken();
            tokenMap.put(id, token);
            System.out.println("alloc id:" + id + ", token:" + BytesUtil.toHexString(token));
            byte[] proxyPortBytes = BytesUtil.toBytesH(proxyPort);
            // 0x00认证失败 0x01认证成功
            byte[] outData = BytesUtil.concatBytes(new byte[]{0x01}, proxyPortBytes, idBytes, token);
            encryptDecrypt(outData);
            buffer.writeBytes(outData);
            ctx.writeAndFlush(buffer);
        } else {
            System.out.println("auth fail!");
            // 0x00认证失败 0x01认证成功
            byte[] outData = new byte[]{(byte) 0x00};
            encryptDecrypt(outData);
            buffer.writeBytes(outData);
            ctx.writeAndFlush(buffer);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        //发生异常，关闭通道
        System.out.println("auth ctx occur error, close connect");
        cause.printStackTrace();
        byte[] idBytes = BytesUtil.hexToBytes(ctx.channel().id().asShortText());
        int id = (int) (BytesUtil.toNumberH(idBytes));
        tokenMap.remove(id);
        ctx.channel().close();
    }

    public static byte[] createToken() {
        Random random = new Random();
        byte[] token = new byte[16];
        for (int i = 0; i < 4; i++) {
            int i1 = random.nextInt();
            byte[] bytes = BytesUtil.toBytesH(i1);
            token[i * 4] = bytes[0];
            token[i * 4 + 1] = bytes[1];
            token[i * 4 + 2] = bytes[2];
            token[i * 4 + 3] = bytes[3];
        }
        return token;
    }

    /**
     * proximal的连接认证
     *
     * @param data
     * @return
     * @throws IOException
     */
    public static boolean proximalAuth(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        int version = buffer.get();     //版本，ocean1的值是0x01
        int userLen = buffer.getInt(); //用户名的长度
        byte[] username = new byte[userLen];
        buffer.get(username);
        int pwdLen = buffer.getInt();
        byte[] password = new byte[pwdLen];
        buffer.get(password);

        String user = new String(username, StandardCharsets.UTF_8);
        String configPwd = userProperties.getProperty(user);
        String pwd = new String(password, StandardCharsets.UTF_8);
        System.out.println("auth username:" + user + ", password:" + pwd);
        if (StringUtils.isEmpty(configPwd)) {
            System.out.println("no user：" + user);
            return false;
        }
        if (configPwd.equals(pwd)) {
            return true;
        } else {
            System.out.println("password error");
        }
        return false;
    }

    public static void encryptDecrypt(byte[] data) {
        for (int i = 0; i < data.length; i++) {
            data[i] ^= (secretKey[i % secretKey.length]);
        }
    }

}
