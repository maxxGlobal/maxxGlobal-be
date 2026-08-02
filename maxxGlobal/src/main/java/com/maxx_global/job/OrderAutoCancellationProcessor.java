package com.maxx_global.job;

import com.maxx_global.entity.Order;
import com.maxx_global.enums.OrderStatus;
import com.maxx_global.event.OrderAutoCancelledEvent;
import com.maxx_global.repository.OrderRepository;
import com.maxx_global.service.DiscountService;
import com.maxx_global.service.OrderStockReturnService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class OrderAutoCancellationProcessor {
    private final OrderRepository orderRepository;
    private final OrderStockReturnService stockReturnService;
    private final DiscountService discountService;
    private final ApplicationEventPublisher eventPublisher;

    public OrderAutoCancellationProcessor(OrderRepository orderRepository,
                                          OrderStockReturnService stockReturnService,
                                          DiscountService discountService,
                                          ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.stockReturnService = stockReturnService;
        this.discountService = discountService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean cancelExpiredOrder(Long orderId, String reason, int hoursWaited, boolean publishEvent) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));
        if (order.getOrderStatus() != OrderStatus.EDITED_PENDING_APPROVAL) {
            return false;
        }

        stockReturnService.returnOrderStock(order, order.getUser(), "AUTO_CANCELLED");
        if (order.getAppliedDiscount() != null) {
            discountService.removeDiscountUsage(order.getId());
        }
        addCancellationNotes(order, reason, hoursWaited);
        order.setOrderStatus(OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);
        if (publishEvent) {
            eventPublisher.publishEvent(new OrderAutoCancelledEvent(saved, reason, hoursWaited));
        }
        return true;
    }

    private void addCancellationNotes(Order order, String reason, int hoursWaited) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
        String note = "\n[" + timestamp + " - SİSTEM OTOMATIK İPTALİ]\n" + reason
                + "\nİptal tarihi: " + timestamp + "\nBekleme süresi: " + hoursWaited + " saat";
        order.setAdminNotes((order.getAdminNotes() != null ? order.getAdminNotes() : "") + note);
        order.setNotes((order.getNotes() != null ? order.getNotes() : "")
                + "\n[Sistem notu: düzenleme onay süresi dolduğu için otomatik iptal edildi]");
    }
}
