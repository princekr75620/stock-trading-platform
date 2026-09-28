package service;

import model.SystemSettings;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service managing system configurations and real-time runtime status metrics.
 */
public class SystemSettingsService implements ISystemSettingsService {
    private SystemSettings settings;
    private long serverStartTime;

    public SystemSettingsService() {
        this.settings = new SystemSettings();
        this.serverStartTime = System.currentTimeMillis();
    }

    public SystemSettingsService(SystemSettings settings) {
        this.settings = (settings != null) ? settings : new SystemSettings();
        this.serverStartTime = System.currentTimeMillis();
    }

    @Override
    public SystemSettings getSettings() {
        return settings;
    }

    @Override
    public synchronized void updateSettings(SystemSettings newSettings) {
        if (newSettings != null) {
            this.settings = newSettings;
            this.settings.setLastUpdated(LocalDateTime.now());
        }
    }

    @Override
    public synchronized void toggleMaintenanceMode() {
        this.settings.setMaintenanceMode(!this.settings.isMaintenanceMode());
        this.settings.setLastUpdated(LocalDateTime.now());
    }

    @Override
    public Map<String, String> getSystemStatusMetrics() {
        Map<String, String> metrics = new LinkedHashMap<>();

        long uptimeSeconds = (System.currentTimeMillis() - serverStartTime) / 1000;
        long hours = uptimeSeconds / 3600;
        long minutes = (uptimeSeconds % 3600) / 60;
        long seconds = uptimeSeconds % 60;

        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        long heapUsed = memBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        long heapMax = memBean.getHeapMemoryUsage().getMax() / (1024 * 1024);

        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        int availableProcessors = osBean.getAvailableProcessors();

        metrics.put("System Version", settings.getSystemVersion());
        metrics.put("Platform Name", settings.getExchangeName());
        metrics.put("Market Status", settings.getMarketStatus().name());
        metrics.put("Maintenance Mode", settings.isMaintenanceMode() ? "ENABLED (Locked)" : "DISABLED (Normal)");
        metrics.put("Server Uptime", String.format("%02d hrs : %02d mins : %02d secs", hours, minutes, seconds));
        metrics.put("JVM Memory Usage", String.format("%d MB / %d MB (%.1f%%)", heapUsed, heapMax, (heapUsed * 100.0 / Math.max(1, heapMax))));
        metrics.put("CPU Cores Available", String.valueOf(availableProcessors));
        metrics.put("Active Threads", String.valueOf(Thread.activeCount()));
        metrics.put("Trading Fee", String.format("%.2f%%", settings.getTradingFeePercentage()));
        metrics.put("Last Config Update", settings.getLastUpdated().toString());

        return metrics;
    }
}
