package com.ocean.proxy.server.proximal.service;

import com.ocean.proxy.server.proximal.handler.DistalHandler;
import com.ocean.proxy.server.proximal.util.BytesUtil;
import com.ocean.proxy.server.proximal.util.CustomThreadFactory;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;

import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <b>Description:</b>  <br/>
 * <b>@Author:</b> Ocean <br/>
 * <b>@DateTime:</b> 2024/1/30 11:15
 */
@Slf4j
public class DistalServer {

    /**
     * 与distal交互的线程池。
     * 每个线程负责对一个distal连接进行数据交互
     */
    private static final ExecutorService executorService = Executors.newCachedThreadPool(new CustomThreadFactory("distalConn-"));

    //统计与distal的连接数量
    private static final AtomicInteger count = new AtomicInteger(0);

    //连接池。用队列作为存放连接对象的数据结构。连接取用后，不再向连接池回收
    private static final BlockingQueue<DistalHandler> connectQueue = new LinkedBlockingQueue<>();

    private static String distalAddress;

    private static Integer distalConnectPort;

    private static ConfigReader configReader;

    public static void init(ConfigReader configReader, Integer distalConnectPort){
        DistalServer.configReader = configReader;
        DistalServer.distalAddress = configReader.getDistalAddress();
        DistalServer.distalConnectPort = distalConnectPort;
        String connPool = configReader.getProperties().getProperty("connection.pool.use");
        if (!StringUtils.isEmpty(connPool) && connPool.equals("true")) {
            //连接池最大大小
            String connectPoolMax = configReader.getProperties().getProperty("connection.pool.max");
            //连接池最小大小，小于该值后，将补充连接数到最大
            String connectPoolMin = configReader.getProperties().getProperty("connection.pool.min");
            if (StringUtils.isEmpty(connectPoolMax)) {
                connectPoolMax = "50";
            }
            if (StringUtils.isEmpty(connectPoolMin)) {
                connectPoolMin = "20";
            }
            int max = Integer.parseInt(connectPoolMax);
            int min = Integer.parseInt(connectPoolMin);
            Executors.newScheduledThreadPool(5).scheduleWithFixedDelay(() -> {
                try {
                    if (connectQueue.size() < min) {
                        while (connectQueue.size() < max) {
                            DistalHandler distalHandler = createDistalHandler();
                            if (distalHandler == null) {
                                continue;
                            }
                            connectQueue.offer(distalHandler);
                            log.info("create new connection into pool:" + connectQueue.size());
                        }
                    }
                } catch (Exception e) {
                    log.error("error in create new connection.", e);
                }
            }, 0, 100, TimeUnit.MILLISECONDS);
        }
    }

    public static void useDistalConnect(Socket clientSocket, String targetAddress, Integer targetPort)throws Exception{
        DistalHandler distalHandler;
        if (connectQueue.size() > 0) {
            distalHandler = connectQueue.take();
            while (distalHandler.getEffective() == 2) {
                log.warn("invalid connection +1");
                distalHandler = connectQueue.take();
            }
            log.info("use connection pool, remaining：" + connectQueue.size());
        }else {
            distalHandler = createDistalHandler();
        }
        if (distalHandler == null) {
            return;
        }
        useActive(distalHandler, clientSocket, targetAddress, targetPort);
        waitConnectTarget(distalHandler, targetAddress, targetPort);
    }

    private static DistalHandler createDistalHandler(){
        DistalHandler distalHandler = new DistalHandler();
        createDistalConnect(distalHandler);
        while (distalHandler.getEffective() == 0) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (distalHandler.getEffective() == 2) {
            return null;
        }
        return distalHandler;
    }

    private static void createDistalConnect(DistalHandler distalHandler) {
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
                log.info("start connect distal. connection total: " + count.incrementAndGet());
                ChannelFuture channelFuture = bootstrap.connect(distalAddress, distalConnectPort).sync();
                log.info("success connected.");
                distalHandler.setEffective(1);
                //对通道关闭进行监听
                channelFuture.channel().closeFuture().sync();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                //关闭线程组
                eventExecutors.shutdownGracefully();
                log.info("close connection!  connection total:" + count.decrementAndGet());
                distalHandler.setEffective(2);
            }
        });
    }

    private static void waitConnectTarget(DistalHandler distalHandler, String targetAddress, Integer targetPort){
        int times = 30;
        while (distalHandler.getTargetConnectStatus() == -1) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            times--;
            if (times <= 0) {
                log.error("connect timeout！"+ targetAddress + ":" + targetPort);
                distalHandler.close();
                throw new RuntimeException("connect timeout！"+ targetAddress + ":" + targetPort);
            }
        }
        if (distalHandler.getTargetConnectStatus() == 2) {
            boolean b = false;
            try {
                b = AuthToDistal.distalAuth(configReader);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (!b) {
                log.info("auth fail, close this program");
                System.exit(0);
            }
        }
    }

    private static void useActive(DistalHandler distalHandler, Socket clientSocket, String targetAddress, Integer targetPort) {
        distalHandler.setClientSocket(clientSocket);
        //发送新建连接的数据给distal
        byte[] version = new byte[]{0x01};
        byte[] addressBytes = targetAddress.getBytes(StandardCharsets.UTF_8);
        byte[] addressLen = BytesUtil.toBytesH(addressBytes.length);
        byte[] portBytes = BytesUtil.toBytesH(targetPort);
        byte[] id = AuthToDistal.getId();
        byte[] token = AuthToDistal.getToken();
        byte[] data = BytesUtil.concatBytes(version, id, token, addressLen, addressBytes, portBytes);
        byte[] randomData = RandomUtils.nextBytes(RandomUtils.nextInt(11, 99));
        byte[] sendData = BytesUtil.concatBytes(data, randomData);
        //通过默认密码加密
        AuthToDistal.encryptDecrypt(sendData);
        distalHandler.sendData(sendData);
        log.info("send connect info!");
    }
}
