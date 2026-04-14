package com.example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Configuration
public class BashToolConfig {

    // 对应 Python 中的 input_schema
    public record BashRequest(String command) {}

    @Bean
    @Description("Run a shell command in the current workspace.")
    public Function<BashRequest, String> bashTool() {
        return request -> {
            String command = request.command();
            System.out.println("\033[33m$ " + command + "\033[0m");

            // 危险命令拦截
            List<String> dangerous = List.of("rm -rf /", "sudo", "shutdown", "reboot", "> /dev/");
            if (dangerous.stream().anyMatch(command::contains)) {
                return "Error: Dangerous command blocked";
            }

            try {
                // 等价于 Python 的 subprocess.run(..., shell=True)
                ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
                pb.directory(new File(System.getProperty("user.dir"))); // 当前工作目录
                pb.redirectErrorStream(true); // 将 stderr 合并到 stdout

                Process process = pb.start();
                // 设置 120 秒超时
                boolean finished = process.waitFor(120, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return "Error: Timeout (120s)";
                }

                // 读取输出
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                String result = output.isEmpty() ? "(no output)" : output;
                
                // 截断超长结果 (50000 字符)
                if (result.length() > 50000) {
                    result = result.substring(0, 50000);
                }

                // 在控制台预览前 200 个字符
                System.out.println(result.substring(0, Math.min(result.length(), 200)));

                return result;

            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        };
    }
}