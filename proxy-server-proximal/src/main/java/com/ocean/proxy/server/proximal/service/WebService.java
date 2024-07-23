package com.ocean.proxy.server.proximal.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Description:
 *
 * @author Ocean
 * datetime: 2024/7/1 16:07
 */
@Slf4j
public class WebService {

    public static void startWebservice(Integer port) throws Exception{
        // 创建HTTP服务器，绑定到指定的端口
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        // 创建一个上下文，指定URL路径和处理程序
        server.createContext("/pac", new PacHandler());
        // 启动服务器
        server.setExecutor(null); // 创建一个默认的线程池
        server.start();
        log.info("开启pac服务，端口：{}, 访问路径：{}", port, "/pac/1.pac");
    }

    static class PacHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 获取请求的URI
            String requestURI = exchange.getRequestURI().toString();
            // 提取文件路径
            String fileName = requestURI.replace("/pac/", "");
            InputStream inputStream = this.getClass().getResourceAsStream("/pac/" + fileName);
            OutputStream os = exchange.getResponseBody();

            if (inputStream == null) {
                log.error("{}文件不存在！", fileName);
                byte[] resultBytes = (fileName + " File not found").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, resultBytes.length);
                os.write(resultBytes);
            }else{
                // 读取文件内容
                try {
                    byte[] resultBytes = new byte[0];
                    byte[] data = new byte[1024];
                    int len;
                    while ((len = inputStream.read(data)) != -1) {
                        byte[] temp = new byte[resultBytes.length + len];
                        System.arraycopy(resultBytes,0, temp, 0, resultBytes.length);
                        System.arraycopy(data,0, temp, resultBytes.length, len);
                        resultBytes = temp;
                    }
                    exchange.sendResponseHeaders(200, resultBytes.length);
                    os.write(resultBytes);
                } catch (IOException e) {
                    // 文件读取出错
                    log.error("文件读取出错.", e);
                    byte[] resultBytes = (fileName + " read file error").getBytes();
                    exchange.sendResponseHeaders(500, resultBytes.length);
                    os.write(resultBytes);
                }
            }
            os.close();
        }
    }
}
