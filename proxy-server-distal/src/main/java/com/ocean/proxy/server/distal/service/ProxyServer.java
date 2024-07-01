package com.ocean.proxy.server.distal.service;

import com.ocean.proxy.server.distal.handler.ProxyHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;

@Slf4j
public class ProxyServer {

    /**
     * 开启连接服务，客户端发起新连接的时候触发。
     *
     * @return 新创建的端口
     */
    public static void startProxyServer(Integer proxyPort) {
        Executors.newSingleThreadExecutor().execute(() -> {
            //创建2个线程组
            NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
            NioEventLoopGroup workerGroup = new NioEventLoopGroup(16);
            ChannelFuture channelFuture;
            try {
                //创建服务端的启动对象，设置参数
                ServerBootstrap serverBootstrap = new ServerBootstrap();
                serverBootstrap.group(bossGroup, workerGroup)
                        //设置服务端通道实现类型
                        .channel(NioServerSocketChannel.class)
                        //设置接收连接的队列长度
                        .option(ChannelOption.SO_BACKLOG, 256)
                        //设置保持活动连接状态
                        .childOption(ChannelOption.SO_KEEPALIVE, true)
                        //使用匿名内部类的形式初始化通道对象
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel socketChannel) {
                                //加密后的数据从1024字节变成1040字节，这里设置固定缓存区大小1040，为了接收完整加密数据，才能对数据解密
                                // socketChannel.config().setRecvByteBufAllocator(new FixedRecvByteBufAllocator(1040));
                                //给pipeline管道设置处理器
                                socketChannel.pipeline().addLast(new ProxyHandler());
                            }
                        });
                //绑定端口号，启动服务端
                channelFuture = serverBootstrap.bind(proxyPort).sync();
                log.info("Proxy distal Server is running on " + channelFuture.channel().localAddress());
                //对关闭通道进行监听
                channelFuture.channel().closeFuture().sync();
            } catch (Exception e) {
                e.printStackTrace();
            }finally {
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }
            log.info("proxy server has closed, exit project!");
            System.exit(0);
        });
    }

}
