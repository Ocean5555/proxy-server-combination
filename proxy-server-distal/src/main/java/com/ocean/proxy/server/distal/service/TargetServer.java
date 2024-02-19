package com.ocean.proxy.server.distal.service;

import com.ocean.proxy.server.distal.handler.TargetHandler;
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
 * <b>@DateTime:</b> 2024/1/30 10:44
 */
public class TargetServer {

    private static final ExecutorService executorService = Executors.newCachedThreadPool();

    private static final AtomicInteger count = new AtomicInteger(0);

    public static void createTargetConnect(String targetAddress, Integer targetPort, TargetHandler targetHandler) {
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
                            protected void initChannel(SocketChannel ch) {
                                //添加客户端通道的处理器
                                ch.pipeline().addLast(targetHandler);
                            }
                        });
                //与目标服务建立连接
                System.out.println("start connect target " + targetAddress + ":" + targetPort);
                System.out.println("connection total:" + count.incrementAndGet());
                ChannelFuture channelFuture = bootstrap.connect(targetAddress, targetPort).sync();
                System.out.println("target connect success.");

                //对通道关闭进行监听
                channelFuture.channel().closeFuture().sync();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                //关闭线程组
                eventExecutors.shutdownGracefully();
                System.out.println("close target connect! (" + targetAddress + ":" + targetPort +
                        ") connection total:" + count.decrementAndGet());
            }
        });
    }

}
