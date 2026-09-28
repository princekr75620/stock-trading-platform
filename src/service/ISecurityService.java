package service;

import model.SecurityIncident;
import model.SecuritySettings;

import java.util.List;

/**
 * Interface defining operations for managing platform financial security,
 * auditing policies, and incident telemetry.
 */
public interface ISecurityService {
    SecuritySettings getSecuritySettings();
    void updateSecuritySettings(SecuritySettings newSettings);
    void recordIncident(SecurityIncident incident);
    List<SecurityIncident> getAllIncidents();
    List<SecurityIncident> getRecentIncidents(int limit);
    int getOpenIncidentCount();
    String getSecurityHealthScore();
}
