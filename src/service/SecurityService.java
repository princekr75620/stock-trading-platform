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
                "Trade order of ₹75,000 flagged for secondary verification threshold",
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
        int score = 100;
        int openCritical = 0;
        int openHigh = 0;
        int openMedium = 0;
        int openLow = 0;

        for (SecurityIncident inc : incidents) {
            if (inc.getStatus() == SecurityIncident.Status.OPEN ||
                inc.getStatus() == SecurityIncident.Status.INVESTIGATING) {
                switch (inc.getSeverity()) {
                    case CRITICAL: openCritical++; break;
                    case HIGH: openHigh++; break;
                    case MEDIUM: openMedium++; break;
                    case LOW: openLow++; break;
                }
            }
        }

        score -= openCritical * 22;
        score -= openHigh * 10;
        score -= openMedium * 5;
        score -= openLow * 2;

        if (settings != null) {
            if (!settings.isAutomaticAccountLockout()) score -= 12;
            if (!settings.isRequireSpecialChar() || settings.getMinPasswordLength() < 8) score -= 8;
            if (!settings.isIpAnomalyDetection()) score -= 5;
            if (settings.getSessionTimeoutMinutes() > 60) score -= 5;
            if (!settings.isRequireTwoStepForHighValueTrades()) score -= 5;
        }

        score = Math.max(0, Math.min(100, score));
        int open = openCritical + openHigh + openMedium + openLow;

        if (score >= 95 && open == 0) {
            return "OPTIMAL (" + score + "/100)";
        }
        if (score >= 85) {
            return "GOOD (" + score + "/100)" + (open > 0 ? " - Minor active incidents" : "");
        }
        if (score >= 65) {
            return "ELEVATED (" + score + "/100) - Action needed";
        }
        return "CRITICAL (" + score + "/100) - Immediate intervention required";
    }
}
