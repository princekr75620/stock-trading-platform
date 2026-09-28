package service;

import model.SecurityIncident;
import model.SecuritySettings;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements financial security monitoring, policy updates, and security incident tracking.
 */
public class SecurityService implements ISecurityService {
    private SecuritySettings settings;
    private List<SecurityIncident> incidents;

    public SecurityService() {
        this.settings = new SecuritySettings();
        this.incidents = new ArrayList<>();
        seedDefaultIncidents();
    }

    public SecurityService(SecuritySettings settings, List<SecurityIncident> incidents) {
        this.settings = (settings != null) ? settings : new SecuritySettings();
        this.incidents = (incidents != null) ? new ArrayList<>(incidents) : new ArrayList<>();
    }

    private void seedDefaultIncidents() {
        incidents.add(new SecurityIncident(
                "INC-1001",
                "FAILED_LOGIN_SPIKE",
                "Multiple failed logins detected from IP 192.168.1.105 (blocked by rate limiter)",
                SecurityIncident.Severity.MEDIUM,
                LocalDateTime.now().minusHours(4),
                SecurityIncident.Status.RESOLVED,
                "192.168.1.105"
        ));
        incidents.add(new SecurityIncident(
                "INC-1002",
                "API_SIGNATURE_MISMATCH",
                "Invalid HMAC signature on simulated external feed connector",
                SecurityIncident.Severity.LOW,
                LocalDateTime.now().minusHours(2),
                SecurityIncident.Status.RESOLVED,
                "Gateway-01"
        ));
        incidents.add(new SecurityIncident(
                "INC-1003",
                "HIGH_VALUE_TRADE_ALERT",
                "Trade order of $75,000 flagged for secondary verification threshold",
                SecurityIncident.Severity.HIGH,
                LocalDateTime.now().minusMinutes(45),
                SecurityIncident.Status.OPEN,
                "Trader-002"
        ));
    }

    @Override
    public SecuritySettings getSecuritySettings() {
        return settings;
    }

    @Override
    public synchronized void updateSecuritySettings(SecuritySettings newSettings) {
        if (newSettings != null) {
            this.settings = newSettings;
            this.settings.setLastUpdated(LocalDateTime.now());
        }
    }

    @Override
    public synchronized void recordIncident(SecurityIncident incident) {
        if (incident != null) {
            this.incidents.add(incident);
        }
    }

    @Override
    public List<SecurityIncident> getAllIncidents() {
        return new ArrayList<>(incidents);
    }

    @Override
    public List<SecurityIncident> getRecentIncidents(int limit) {
        List<SecurityIncident> copy = new ArrayList<>(incidents);
        int start = Math.max(0, copy.size() - limit);
        return copy.subList(start, copy.size());
    }

    @Override
    public int getOpenIncidentCount() {
        int count = 0;
        for (SecurityIncident inc : incidents) {
            if (inc.getStatus() == SecurityIncident.Status.OPEN ||
                inc.getStatus() == SecurityIncident.Status.INVESTIGATING) {
                count++;
            }
        }
        return count;
    }

    @Override
    public String getSecurityHealthScore() {
        int open = getOpenIncidentCount();
        if (open == 0) return "OPTIMAL (98/100)";
        if (open <= 2) return "GOOD (92/100) - Minor active incidents";
        if (open <= 5) return "ELEVATED (78/100) - Action needed";
        return "CRITICAL (55/100) - Immediate intervention required";
    }
}
