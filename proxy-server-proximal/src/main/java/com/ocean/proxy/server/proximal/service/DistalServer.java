package com.ocean.proxy.server.proximal.service;

import com.ocean.proxy.server.proximal.handler.DistalHandler;
import com.ocean.proxy.server.proximal.util.BytesUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.oio.OioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * <b>Description:</b>  <br/>
 * <b>@Author:</b> Ocean <br/>
 * <b>@DateTime:</b> 2024/1/30 11:15
 */
public class DistalServer {

    private static String distalAddress;

    private static Integer distalConnectPort;

    public static void setDistalAddress(String distalAddress) {
        DistalServer.distalAddress = distalAddress;
    }

    public static void setDistalConnectPort(Integer distalConnectPort) {
        DistalServer.distalConnectPort = distalConnectPort;
    }

    public static void createDistalConnect(DistalHandler distalHandler, String targetAddress, Integer targetPort) {
        Executors.newSingleThreadExecutor().execute(() -> {
            NioEventLoopGroup eventExecutors = new NioEventLoopGroup();
            try {
                //创建bootstrap对象，配置参数
                Bootstrap bootstrap = new Bootstrap();
                //设置线程组
                bootstrap.group(eventExecutors)
                        //设置客户端的通道实现类型
                        .channel(NioSocketChannel.class)
                        //使用匿名内部类初始化通道
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel socketChannel) throws Exception {
                                // 加密后的数据从1024字节变成1040字节，这里设置固定缓存区大小1040，为了接收完整加密数据，才能对数据解密
                                socketChannel.config().setRecvByteBufAllocator(new FixedRecvByteBufAllocator(1040));
                                //添加客户端通道的处理器
                                socketChannel.pipeline().addLast(distalHandler);
                            }
                        });
                //与目标服务建立连接
                System.out.println("start connect distal " + distalAddress + ":" + distalConnectPort);
                ChannelFuture channelFuture = bootstrap.connect(distalAddress, distalConnectPort).sync();
                System.out.println("success connected");
                sendConnectData(channelFuture, targetAddress, targetPort);
                //对通道关闭进行监听
                channelFuture.channel().closeFuture().sync();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                //关闭线程组
                eventExecutors.shutdownGracefully();
            }
        });

        int times = 20;
        while (distalHandler.getConnected() == null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            times--;
            if (times <= 0) {
                throw new RuntimeException("token auth timeout！");
            }
        }
        if (!distalHandler.getConnected()) {
            //token认证失败，可能distal重启了，需要重新认证获取新的token
            System.out.println("token auth failed！");
            try {
                boolean b = AuthToDistal.distalAuth();
                if (!b) {
                    System.out.println("auth fail, close this program");
                    System.exit(0);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            throw new RuntimeException("token auth failed！");
        }
    }

    private static void sendConnectData(ChannelFuture channelFuture, String targetAddress, Integer targetPort){
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
        Channel distalChannel = channelFuture.channel();
        if (distalChannel.isOpen()) {
            ByteBuf buf = Unpooled.buffer();
            buf.writeBytes(data);
            distalChannel.writeAndFlush(buf);
            System.out.println("send connect info!");
        }
    }
}
