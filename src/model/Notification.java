package model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents a user notification (Trade confirmations, Market updates, System alerts).
 */
public class Notification implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public enum NotificationType {
        TRADE_BUY,
        TRADE_SELL,
        MARKET_ALERT,
        SECURITY_ALERT,
        SYSTEM_EVENT
    }

    private String notificationId;
    private String recipientId;
    private String title;
    private String message;
    private NotificationType type;
    private LocalDateTime timestamp;
    private boolean isRead;

    public Notification(String notificationId, String recipientId, String title, String message, NotificationType type) {
        this.notificationId = notificationId;
        this.recipientId = recipientId;
        this.title = title;
        this.message = message;
        this.type = type;
        this.timestamp = LocalDateTime.now();
        this.isRead = false;
    }

    public Notification(String notificationId, String recipientId, String title, String message,
                        NotificationType type, LocalDateTime timestamp, boolean isRead) {
        this.notificationId = notificationId;
        this.recipientId = recipientId;
        this.title = title;
        this.message = message;
        this.type = type;
        this.timestamp = timestamp;
        this.isRead = isRead;
    }

    public String getNotificationId() {
        return notificationId;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public NotificationType getType() {
        return type;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getFormattedTimestamp() {
        return timestamp != null ? timestamp.format(FORMATTER) : "N/A";
    }

    public boolean isRead() {
        return isRead;
    }

    public void markAsRead() {
        this.isRead = true;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s | %s - %s (Status: %s)",
                getFormattedTimestamp(), type, title, message, (isRead ? "Read" : "NEW"));
    }
}
