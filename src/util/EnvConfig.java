package util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Reliable environment variable loader for standalone Java execution and Node.js-spawned JVMs.
 * Precedence order (highest to lowest):
 * 1. Real OS process environment variables (System.getenv)
 * 2. JVM system properties (-D / System.setProperty, useful for isolated tests)
 * 3. Optional local .env file values (never overrides OS env; never logs secret values)
 */
public final class EnvConfig {
    private static volatile Map<String, String> dotEnvCache = null;

    private EnvConfig() {}

    public static String get(String key) {
        if (key == null || key.trim().isEmpty()) return null;
        String envVal = System.getenv(key);
        if (envVal != null && !envVal.trim().isEmpty()) {
            return envVal.trim();
        }
        String sysProp = System.getProperty(key);
        if (sysProp != null && !sysProp.trim().isEmpty()) {
            return sysProp.trim();
        }
        String fileVal = loadDotEnv().get(key);
        if (fileVal != null && !fileVal.trim().isEmpty()) {
            return fileVal.trim();
        }
        return null;
    }

    public static String get(String key, String fallback) {
        String val = get(key);
        return (val != null && !val.isEmpty()) ? val : fallback;
    }

    public static String first(String primary, String secondary, String fallback) {
        String v1 = get(primary);
        if (v1 != null && !v1.isEmpty()) return v1;
        String v2 = get(secondary);
        if (v2 != null && !v2.isEmpty()) return v2;
        return fallback;
    }

    public static int getPositiveInt(String primary, String secondary, int fallback) {
        String val = first(primary, secondary, null);
        if (val == null || val.isEmpty()) return fallback;
        try {
            int parsed = Integer.parseInt(val);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static long getPositiveLong(String primary, String secondary, long fallback) {
        String val = first(primary, secondary, null);
        if (val == null || val.isEmpty()) return fallback;
        try {
            long parsed = Long.parseLong(val);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static boolean isProductionMode() {
        String env = first("APP_ENV", "NODE_ENV", "development");
        return "production".equalsIgnoreCase(env);
    }

    public static void clearDotEnvCacheForTests() {
        dotEnvCache = null;
    }

    public static Map<String, String> parseDotEnvFile(File dotEnv) {
        Map<String, String> parsed = new HashMap<>();
        if (dotEnv == null || !dotEnv.isFile() || !dotEnv.canRead()) {
            return parsed;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(dotEnv, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                if (trimmed.startsWith("export ")) {
                    trimmed = trimmed.substring(7).trim();
                }
                int eqIdx = trimmed.indexOf('=');
                if (eqIdx <= 0) continue;
                String k = trimmed.substring(0, eqIdx).trim();
                String v = trimmed.substring(eqIdx + 1).trim();
                if ((v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2)
                        || (v.startsWith("'") && v.endsWith("'") && v.length() >= 2)) {
                    v = v.substring(1, v.length() - 1);
                }
                if (!k.isEmpty()) {
                    parsed.put(k, v);
                }
            }
        } catch (Exception ignored) {
            // Do not fail startup if optional .env cannot be read when OS env is present
        }
        return parsed;
    }

    private static Map<String, String> loadDotEnv() {
        Map<String, String> cached = dotEnvCache;
        if (cached != null) return cached;

        synchronized (EnvConfig.class) {
            if (dotEnvCache != null) return dotEnvCache;
            Map<String, String> parsed = parseDotEnvFile(new File(".env"));
            dotEnvCache = Collections.unmodifiableMap(parsed);
            return dotEnvCache;
        }
    }
}
