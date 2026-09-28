package service;

import model.SystemSettings;

import java.util.Map;

/**
 * Interface defining operations for managing platform settings and monitoring system health.
 */
public interface ISystemSettingsService {
    SystemSettings getSettings();
    void updateSettings(SystemSettings settings);
    Map<String, String> getSystemStatusMetrics();
    void toggleMaintenanceMode();
}
