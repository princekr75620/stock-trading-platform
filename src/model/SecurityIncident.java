package model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents a recorded financial data security incident (e.g. failed login attempts,
 * anomalous trading activity, unauthorized access attempts).
 */
public class SecurityIncident implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public enum Severity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    public enum Status {
        OPEN,
        INVESTIGATING,
        RESOLVED
    }

    private String incidentId;
    private String incidentType;
    private String description;
    private Severity severity;
    private LocalDateTime timestamp;
    private Status status;
    private String affectedUserOrIp;

    public SecurityIncident(String incidentId, String incidentType, String description,
                            Severity severity, String affectedUserOrIp) {
        this.incidentId = incidentId;
        this.incidentType = incidentType;
        this.description = description;
        this.severity = severity;
        this.timestamp = LocalDateTime.now();
        this.status = Status.OPEN;
        this.affectedUserOrIp = affectedUserOrIp;
    }

    public SecurityIncident(String incidentId, String incidentType, String description,
                            Severity severity, LocalDateTime timestamp, Status status, String affectedUserOrIp) {
        this.incidentId = incidentId;
        this.incidentType = incidentType;
        this.description = description;
        this.severity = severity;
        this.timestamp = timestamp;
        this.status = status;
        this.affectedUserOrIp = affectedUserOrIp;
    }

    public String getIncidentId() {
        return incidentId;
    }

    public String getIncidentType() {
        return incidentType;
    }

    public String getDescription() {
        return description;
    }

    public Severity getSeverity() {
        return severity;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getFormattedTimestamp() {
        return timestamp != null ? timestamp.format(FORMATTER) : "N/A";
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getAffectedUserOrIp() {
        return affectedUserOrIp;
    }

    @Override
    public String toString() {
        return String.format("[%s] #%s | %-8s | %-16s | Target: %-12s | Status: %-13s | %s",
                getFormattedTimestamp(), incidentId, severity, incidentType, affectedUserOrIp, status, description);
    }
}
