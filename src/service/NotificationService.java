package service;

import model.Alert;
import model.Notification;
import model.Stock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service managing user notifications and stock price threshold alerts.
 */
public class NotificationService implements INotificationService {
    // Map of User ID -> List of Notifications
    private Map<String, List<Notification>> userNotifications;
    // Map of Trader ID -> List of Alerts
    private Map<String, List<Alert>> traderAlerts;

    public NotificationService() {
        this.userNotifications = new HashMap<>();
        this.traderAlerts = new HashMap<>();
    }

    public NotificationService(Map<String, List<Notification>> notifications, Map<String, List<Alert>> alerts) {
        this.userNotifications = (notifications != null) ? new HashMap<>(notifications) : new HashMap<>();
        this.traderAlerts = (alerts != null) ? new HashMap<>(alerts) : new HashMap<>();
    }

    @Override
    public synchronized void sendNotification(Notification notification) {
        if (notification == null || notification.getRecipientId() == null) return;
        userNotifications
                .computeIfAbsent(notification.getRecipientId(), k -> new ArrayList<>())
                .add(0, notification); // Prepend so most recent is first
    }

    @Override
    public List<Notification> getNotificationsForUser(String userId) {
        if (userId == null) return new ArrayList<>();
        return new ArrayList<>(userNotifications.getOrDefault(userId, new ArrayList<>()));
    }

    @Override
    public List<Notification> getUnreadNotificationsForUser(String userId) {
        List<Notification> unread = new ArrayList<>();
        if (userId == null) return unread;
        List<Notification> all = userNotifications.getOrDefault(userId, new ArrayList<>());
        for (Notification n : all) {
            if (!n.isRead()) unread.add(n);
        }
        return unread;
    }

    @Override
    public synchronized void markAllAsRead(String userId) {
        if (userId == null) return;
        List<Notification> list = userNotifications.get(userId);
        if (list != null) {
            for (Notification n : list) {
                n.markAsRead();
            }
        }
    }

    @Override
    public synchronized void createAlert(Alert alert) {
        if (alert == null || alert.getTraderId() == null) return;
        traderAlerts
                .computeIfAbsent(alert.getTraderId(), k -> new ArrayList<>())
                .add(alert);
    }

    @Override
    public List<Alert> getAlertsForTrader(String traderId) {
        if (traderId == null) return new ArrayList<>();
        return new ArrayList<>(traderAlerts.getOrDefault(traderId, new ArrayList<>()));
    }

    @Override
    public synchronized void checkAlertsAgainstStock(Stock stock) {
        if (stock == null) return;
        for (Map.Entry<String, List<Alert>> entry : traderAlerts.entrySet()) {
            String traderId = entry.getKey();
            List<Alert> alerts = entry.getValue();
            for (Alert alert : alerts) {
                if (!alert.isTriggered() && alert.checkCondition(stock)) {
                    // Generate a Notification for the Trader!
                    Notification notification = new Notification(
                            "NOTIF-ALERT-" + System.currentTimeMillis() % 10000,
                            traderId,
                            "PRICE ALERT: " + stock.getSymbol(),
                            "Stock " + stock.getSymbol() + " has reached $" + stock.getCurrentPrice() +
                                    " (" + alert.getCondition() + " " + alert.getTargetValue() + ")",
                            Notification.NotificationType.MARKET_ALERT
                    );
                    sendNotification(notification);
                }
            }
        }
    }

    public Map<String, List<Notification>> getUserNotificationsMap() {
        return userNotifications;
    }

    public Map<String, List<Alert>> getTraderAlertsMap() {
        return traderAlerts;
    }
}
