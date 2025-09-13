//package com.maxx_global.event;
//
//import com.maxx_global.service.StockTrackerService;
//import org.springframework.context.event.EventListener;
//import org.springframework.scheduling.annotation.Async;
//import org.springframework.stereotype.Component;
//
//import java.util.logging.Logger;
//
//@Component
//public class OrderStockEventListener {
//
//    private static final Logger logger = Logger.getLogger(OrderStockEventListener.class.getName());
//
//    private final StockTrackerService stockMovementService;
//
//    public OrderStockEventListener(StockTrackerService stockMovementService) {
//        this.stockMovementService = stockMovementService;
//    }
//
//    @EventListener
//    @Async
//    public void handleOrderCreated(OrderCreatedEvent event) {
//        logger.info("Order created - creating stock movements: " + event.order().getOrderNumber());
//
//        try {
//            // Sipariş oluşturulduğunda otomatik stok çıkışı yap
//            stockMovementService.createMovementForOrder(event.order(), true);
//        } catch (Exception e) {
//            logger.severe("Error creating stock movements for new order: " + e.getMessage());
//        }
//    }
//
//    @EventListener
//    @Async
//    public void handleOrderCancelled(OrderCancelledEvent event) {
//        logger.info("Order cancelled - reversing stock movements: " + event.order().getOrderNumber());
//
//        try {
//            // Sipariş iptal edildiğinde stok iadesi yap
//            stockMovementService.createMovementForOrder(event.order(), false);
//        } catch (Exception e) {
//            logger.severe("Error reversing stock movements for cancelled order: " + e.getMessage());
//        }
//    }
//}