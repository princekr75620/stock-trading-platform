package service;

import model.Alert;
import model.Notification;
import model.Stock;

import java.util.List;

/**
 * Interface for notification delivery, price alerts, and preference management.
 */
public interface INotificationService {
    void sendNotification(Notification notification);
    List<Notification> getNotificationsForUser(String userId);
    List<Notification> getUnreadNotificationsForUser(String userId);
    void markAllAsRead(String userId);
    void createAlert(Alert alert);
    List<Alert> getAlertsForTrader(String traderId);
    void checkAlertsAgainstStock(Stock stock);
}
