package com.ocean.proxy.server.proximal.service;

import com.ocean.proxy.server.proximal.handler.DistalHandler;
import com.ocean.proxy.server.proximal.util.CustomThreadFactory;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <b>Description:</b>  <br/>
 * <b>@Author:</b> Ocean <br/>
 * <b>@DateTime:</b> 2024/1/30 11:15
 */
public class DistalServer {

    private static final ExecutorService executorService = Executors.newCachedThreadPool(new CustomThreadFactory("distalConnectThread-"));

    private static final AtomicInteger count = new AtomicInteger(0);

    private static String distalAddress;

    private static Integer distalConnectPort;

    public static void setDistalAddress(String distalAddress) {
        DistalServer.distalAddress = distalAddress;
    }

    public static void setDistalConnectPort(Integer distalConnectPort) {
        DistalServer.distalConnectPort = distalConnectPort;
    }

    public static void createDistalConnect(DistalHandler distalHandler) {
        executorService.execute(() -> {
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
                                // socketChannel.config().setRecvByteBufAllocator(new FixedRecvByteBufAllocator(1040));
                                //添加客户端通道的处理器
                                socketChannel.pipeline().addLast(distalHandler);
                            }
                        });
                //与目标服务建立连接
                System.out.println("start connect distal " + distalAddress + ":" + distalConnectPort);
                ChannelFuture channelFuture = bootstrap.connect(distalAddress, distalConnectPort).sync();
                System.out.println("success connected. connection total: " + count.incrementAndGet());
                //对通道关闭进行监听
                channelFuture.channel().closeFuture().sync();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                //关闭线程组
                eventExecutors.shutdownGracefully();
                System.out.println("close connection! (" + distalHandler.getTargetAddress() + ":" + distalHandler.getTargetPort() +
                        ") connection total:" + count.decrementAndGet());
            }
        });

        int times = 20;
        while (distalHandler.getTargetConnected() == null) {
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
        if (!distalHandler.getTargetConnected()) {
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

}
