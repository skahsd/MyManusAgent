package cn.mymanus.manus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication
public class MyManusApplication {

    public static void main(String[] args) {
        // 从项目根目录加载 .env 到系统属性
        loadEnvFile();
        SpringApplication.run(MyManusApplication.class, args);
    }

    /**
     * 从当前目录及父目录中查找 .env 文件，逐行解析 KEY=VALUE 并写入系统属性。
     */
    private static void loadEnvFile() {
        Path envPath = findEnvFile(Paths.get(System.getProperty("user.dir")));
        if (envPath == null) {
            System.err.println("⚠ WARNING: .env file not found! API calls will fail with 401.");
            return;
        }
        try {
            for (String line : Files.readAllLines(envPath)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq > 0) {
                    String key = line.substring(0, eq).trim();
                    String value = line.substring(eq + 1).trim();
                    System.setProperty(key, value);
                }
            }
            String apiKey = System.getProperty("VOLCES_API_KEY", "");
            if (!apiKey.isEmpty()) {
                System.out.println("✓ Loaded VOLCES_API_KEY from " + envPath + " (length=" + apiKey.length() + ")");
            }
        } catch (IOException e) {
            System.err.println("⚠ Failed to read .env: " + e.getMessage());
        }
    }

    private static Path findEnvFile(Path start) {
        for (Path dir = start.toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve(".env");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

}
