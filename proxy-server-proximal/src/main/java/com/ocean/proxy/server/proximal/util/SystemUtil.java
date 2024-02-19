package com.ocean.proxy.server.proximal.util;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Description:
 *
 * @author: Ocean
 * DateTime: 2024/2/19 14:53
 */
@Slf4j
public class SystemUtil {

    private static String os;

    static {
        os = System.getProperty("os.name").toLowerCase();
        log.info("os.name=" + os);
        if (os.contains("win")) {
            os = "Windows";
        } else if (os.contains("nix") || os.contains("nux") || os.contains("mac")) {
            os = "Linux";
        } else {
            os= "Other OS";
        }
    }

    public static String getTaskByPort(int portNumber){
        if (os.equalsIgnoreCase("Windows")) {
            return getWindowsTaskByPort(portNumber);
        }
        return "";
    }

    private static String getWindowsTaskByPort(int portNumber) {
        try {
            String regex = "\\s+\\w+\\s+[\\d\\.]+:" + portNumber+".+";

            // 执行 netstat 命令
            Process netstatProcess = Runtime.getRuntime().exec("netstat -ano");
            BufferedReader netstatReader = new BufferedReader(new InputStreamReader(netstatProcess.getInputStream()));

            String netstatLine;
            while ((netstatLine = netstatReader.readLine()) != null) {
                if (netstatLine.matches(regex)) {
                    // 获取对应的进程ID
                    String[] netstatTokens = netstatLine.trim().split("\\s+");
                    String processId = netstatTokens[netstatTokens.length - 1];
                    // 执行 tasklist 命令，获取进程信息
                    Process tasklistProcess = Runtime.getRuntime().exec("tasklist /fi \"PID eq " + processId + "\"");
                    BufferedReader tasklistReader = new BufferedReader(new InputStreamReader(tasklistProcess.getInputStream()));
                    String tasklistLine;
                    int i = 0;
                    while ((tasklistLine = tasklistReader.readLine()) != null) {
                        i++;
                        if (i == 4) {
                            return tasklistLine.split(" ")[0];
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }
}
