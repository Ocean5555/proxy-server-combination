package com.ocean.proxy.server.distal.service;

import com.ocean.proxy.server.distal.handler.AuthenticationHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;

/**
 * <b>Description:</b>  <br/>
 * <b>@Author:</b> Ocean <br/>
 * <b>@DateTime:</b> 2024/1/29 14:32
 */
@Slf4j
public class AuthServer {

    public static void startAuthServer(Integer authPort, Integer proxyPort, String authSecret) {
        Executors.newSingleThreadExecutor().execute(() -> {
            //创建2个线程组
            NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
            NioEventLoopGroup workerGroup = new NioEventLoopGroup(2);
            try {
                //创建服务端的启动对象，设置参数
                ServerBootstrap serverBootstrap = new ServerBootstrap();
                serverBootstrap.group(bossGroup, workerGroup)
                        //设置服务端通道实现类型
                        .channel(NioServerSocketChannel.class)
                        //设置接收连接的队列长度
                        .option(ChannelOption.SO_BACKLOG, 2)
                        //设置保持活动连接状态
                        .childOption(ChannelOption.SO_KEEPALIVE, true)
                        //使用匿名内部类的形式初始化通道对象
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel socketChannel) throws Exception {
                                //给pipeline管道设置处理器
                                socketChannel.pipeline().addLast(new AuthenticationHandler(proxyPort, authSecret));
                            }
                        });
                //绑定端口号，启动服务端
                ChannelFuture channelFuture = serverBootstrap.bind(authPort).sync();
                log.info("Auth Server is running on " + channelFuture.channel().localAddress());
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
