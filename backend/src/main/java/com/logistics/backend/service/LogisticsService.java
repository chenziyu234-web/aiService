package com.logistics.backend.service;

import com.logistics.backend.model.Order;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LogisticsService {

    private final Map<String, Order> orderDb = new ConcurrentHashMap<>();

    public LogisticsService() {
        // Mock data (模拟数据)
        List<String> history1 = new ArrayList<>();
        history1.add("2023-10-01: 订单已创建");
        history1.add("2023-10-02: 从上海分拨中心发出");
        
        orderDb.put("1001", Order.builder()
                .orderId("1001")
                .customerName("张三")
                .status("运输中")
                .currentLocation("杭州")
                .userId("user_001")
                .history(history1)
                .urgent(false)
                .humanInterventionNeeded(false)
                .build());

        List<String> history2 = new ArrayList<>();
        history2.add("2023-10-05: 订单已创建");
        
        orderDb.put("1002", Order.builder()
                .orderId("1002")
                .customerName("李四")
                .status("待发货")
                .currentLocation("一号仓库")
                .userId("user_001")
                .history(history2)
                .urgent(false)
                .humanInterventionNeeded(false)
                .build());
    }

    public Order getOrder(String orderId) {
        return orderDb.get(orderId);
    }
    
    public List<Order> getUserOrders(String userId) {
        List<Order> userOrders = new ArrayList<>();
        for (Order order : orderDb.values()) {
            if (userId != null && userId.equals(order.getUserId())) {
                userOrders.add(order);
            }
        }
        return userOrders;
    }

    public String urgeOrder(String orderId) {
        Order order = orderDb.get(orderId);
        if (order == null) return "未找到订单";
        
        order.setUrgent(true);
        // Simulate sending IM
        sendIMToMerchant(orderId, "客户正在催单！请尽快处理！");
        return "已向商家发送催单提醒，订单号: " + orderId;
    }

    public String reportIssue(String orderId, String issue) {
         Order order = orderDb.get(orderId);
        if (order == null) return "未找到订单";

        order.setHumanInterventionNeeded(true);
        // Simulate escalation
        return "问题已记录，因以下原因转接人工客服: " + issue;
    }

    private void sendIMToMerchant(String orderId, String message) {
        System.out.println("[IM 系统] 发送给商家 (订单 " + orderId + "): " + message);
    }
}
