package com.logistics.backend.model;

import java.util.List;

public class Order {
    private String orderId;
    private String customerName;
    private String status; // PENDING, SHIPPED, DELIVERED, DAMAGED
    private String currentLocation;
    private String userId;
    private List<String> history;
    private boolean urgent;
    private boolean humanInterventionNeeded;

    public Order() {}

    public Order(String orderId, String customerName, String status, String currentLocation, String userId, List<String> history, boolean urgent, boolean humanInterventionNeeded) {
        this.orderId = orderId;
        this.customerName = customerName;
        this.status = status;
        this.currentLocation = currentLocation;
        this.userId = userId;
        this.history = history;
        this.urgent = urgent;
        this.humanInterventionNeeded = humanInterventionNeeded;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCurrentLocation() { return currentLocation; }
    public void setCurrentLocation(String currentLocation) { this.currentLocation = currentLocation; }

    public List<String> getHistory() { return history; }
    public void setHistory(List<String> history) { this.history = history; }

    public boolean isUrgent() { return urgent; }
    public void setUrgent(boolean urgent) { this.urgent = urgent; }

    public boolean isHumanInterventionNeeded() { return humanInterventionNeeded; }
    public void setHumanInterventionNeeded(boolean humanInterventionNeeded) { this.humanInterventionNeeded = humanInterventionNeeded; }

    // Simple Builder
    public static class Builder {
        private String orderId;
        private String customerName;
        private String status;
        private String currentLocation;
        private String userId;
        private List<String> history;
        private boolean urgent;
        private boolean humanInterventionNeeded;

        public Builder orderId(String orderId) { this.orderId = orderId; return this; }
        public Builder customerName(String customerName) { this.customerName = customerName; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder currentLocation(String currentLocation) { this.currentLocation = currentLocation; return this; }
        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder history(List<String> history) { this.history = history; return this; }
        public Builder urgent(boolean urgent) { this.urgent = urgent; return this; }
        public Builder humanInterventionNeeded(boolean humanInterventionNeeded) { this.humanInterventionNeeded = humanInterventionNeeded; return this; }
        
        public Order build() {
            return new Order(orderId, customerName, status, currentLocation, userId, history, urgent, humanInterventionNeeded);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
