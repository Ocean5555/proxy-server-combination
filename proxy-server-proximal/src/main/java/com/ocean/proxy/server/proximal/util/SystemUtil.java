package com.ocean.proxy.server.proximal.util;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

/**
 * Description:
 *
 * @author: Ocean
 * DateTime: 2024/2/19 14:53
 */
@Slf4j
public class SystemUtil {

    private static String os;

    public static boolean isPac = false;

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

    public static void startSystemProxy(String address) {
        if (os.equalsIgnoreCase("Windows")) {
            if (isPac) {
                startWindowsSystemProxyPac(address);
            }else{
                startWindowsSystemProxy(address);
            }
        }
    }

    public static void closeSystemProxy(){
        if (os.equalsIgnoreCase("Windows")) {
            if (isPac) {
                closeWindowsSystemProxyPac();
            }else{
                closeWindowsSystemProxy();
            }
        }
    }

    public static String getTaskByPort(int portNumber){
        if (os.equalsIgnoreCase("Windows")) {
            return getWindowsTaskByPort(portNumber);
        }
        return "";
    }

    private static void startWindowsSystemProxyPac(String url){
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "reg", "add", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
                    "/v", "AutoConfigURL", "/d", url, "/f"
            );
            processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE);
            processBuilder.redirectError(ProcessBuilder.Redirect.PIPE);
            List<String> command = processBuilder.command();
            log.info("set proxy command: \n"+ String.join(" ", command));
            Process process = processBuilder.start();
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void closeWindowsSystemProxyPac() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "reg", "delete", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
                    "/v", "AutoConfigURL", "/f"
            );
            processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE);
            processBuilder.redirectError(ProcessBuilder.Redirect.PIPE);
            List<String> command = processBuilder.command();
            log.info("close proxy command: \n"+ String.join(" ", command));
            Process process = processBuilder.start();
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void startWindowsSystemProxy(String address){
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "reg", "add", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
                    "/v", "ProxyEnable", "/t", "REG_DWORD", "/d", "1", "/f"
            );
            processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE);
            processBuilder.redirectError(ProcessBuilder.Redirect.PIPE);

            ProcessBuilder processBuilder2 = new ProcessBuilder(
                    "reg", "add", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
                    "/v", "ProxyServer", "/d", address, "/f"
            );
            processBuilder2.redirectOutput(ProcessBuilder.Redirect.PIPE);
            processBuilder2.redirectError(ProcessBuilder.Redirect.PIPE);

            List<String> command = processBuilder.command();
            List<String> command2 = processBuilder2.command();
            log.info("set proxy command: \n"+ String.join(" ", command) +"\n" + String.join(" ", command2));
            Process process = processBuilder.start();
            process.waitFor();
            Process process2 = processBuilder2.start();
            process2.waitFor();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void closeWindowsSystemProxy() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "reg", "add", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
                    "/v", "ProxyEnable", "/t", "REG_DWORD", "/d", "0", "/f"
            );
            processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE);
            processBuilder.redirectError(ProcessBuilder.Redirect.PIPE);

            ProcessBuilder processBuilder2 = new ProcessBuilder(
                    "reg", "add", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
                    "/v", "ProxyServer", "/d", "127.0.0.1:1080", "/f"
            );
            processBuilder2.redirectOutput(ProcessBuilder.Redirect.PIPE);
            processBuilder2.redirectError(ProcessBuilder.Redirect.PIPE);

            List<String> command = processBuilder.command();
            List<String> command2 = processBuilder2.command();
            log.info("close proxy command: \n"+ String.join(" ", command) +"\n" + String.join(" ", command2));
            Process process = processBuilder.start();
            process.waitFor();
            Process process2 = processBuilder2.start();
            process2.waitFor();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
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
