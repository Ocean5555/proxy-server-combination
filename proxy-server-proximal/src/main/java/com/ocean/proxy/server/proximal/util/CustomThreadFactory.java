package com.ocean.proxy.server.proximal.util;

import java.util.concurrent.ThreadFactory;

/**
 * <b>Description:</b>  <br/>
 * <b>@Author:</b> Ocean <br/>
 * <b>@DateTime:</b> 2024/2/2 11:26
 */
public class CustomThreadFactory implements ThreadFactory {

    private int count = 1;

    private final String namePrefix;

    public CustomThreadFactory(String namePrefix) {
        this.namePrefix = namePrefix;
    }

    @Override
    public Thread newThread(Runnable r) {
        // 创建新线程时设置线程名称
        return new Thread(r, namePrefix + count++);
    }

}
