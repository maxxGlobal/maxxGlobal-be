package com.maxx_global.service;

import com.maxx_global.dto.discount.DiscountMapper;
import com.maxx_global.entity.Discount;
import com.maxx_global.entity.DiscountUsage;
import com.maxx_global.entity.AppUser;
import com.maxx_global.entity.Dealer;
import com.maxx_global.entity.Order;
import com.maxx_global.enums.OrderStatus;
import com.maxx_global.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;

class DiscountServiceConcurrencySafetyTest {
    @Test
    void differentOrderCancellationsLockDiscountBeforeEachUsageCountDecrement() {
        DiscountRepository discountRepository = mock(DiscountRepository.class);
        DiscountUsageRepository usageRepository = mock(DiscountUsageRepository.class);
        Discount discount = new Discount();
        discount.setId(4L);
        discount.setUsageCount(5);
        DiscountUsage firstUsage = usage(discount);
        DiscountUsage secondUsage = usage(discount);
        when(usageRepository.findByOrderId(1L)).thenReturn(Optional.of(firstUsage));
        when(usageRepository.findByOrderId(2L)).thenReturn(Optional.of(secondUsage));
        when(discountRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(discount));
        DiscountService service = new DiscountService(discountRepository, mock(DiscountMapper.class),
                mock(OrderRepository.class), usageRepository, mock(ApplicationEventPublisher.class),
                mock(CategoryService.class), mock(LocalizationService.class), mock(ProductService.class),
                mock(DealerService.class), mock(ProductVariantRepository.class));

        service.removeDiscountUsage(1L);
        service.removeDiscountUsage(2L);

        assertEquals(3, discount.getUsageCount());
        verify(discountRepository, times(2)).findByIdForUpdate(4L);
        verify(usageRepository).delete(firstUsage);
        verify(usageRepository).delete(secondUsage);
    }

    @Test
    void differentOrderUsagesLockDiscountBeforeEachUsageCountIncrement() {
        Fixture fixture = fixture(0);

        fixture.service.recordDiscountUsage(fixture.discount, user(1L), new Dealer(), order(1L), BigDecimal.ONE);
        fixture.service.recordDiscountUsage(fixture.discount, user(2L), new Dealer(), order(2L), BigDecimal.ONE);

        assertEquals(2, fixture.discount.getUsageCount());
        verify(fixture.discountRepository, times(2)).findByIdForUpdate(4L);
        verify(fixture.usageRepository, times(2)).save(any(DiscountUsage.class));
    }

    @Test
    void usageSaveFailureDoesNotIncrementLockedDiscount() {
        Fixture fixture = fixture(0);
        when(fixture.usageRepository.save(any(DiscountUsage.class)))
                .thenThrow(new RuntimeException("usage write failed"));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> fixture.service.recordDiscountUsage(
                        fixture.discount, user(1L), new Dealer(), order(1L), BigDecimal.ONE));

        assertEquals(0, fixture.discount.getUsageCount());
        verify(fixture.discountRepository, never()).save(any());
    }

    private Fixture fixture(int usageCount) {
        DiscountRepository discountRepository = mock(DiscountRepository.class);
        DiscountUsageRepository usageRepository = mock(DiscountUsageRepository.class);
        Discount discount = new Discount();
        discount.setId(4L);
        discount.setUsageCount(usageCount);
        when(discountRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(discount));
        DiscountService service = service(discountRepository, usageRepository);
        return new Fixture(service, discount, discountRepository, usageRepository);
    }

    private DiscountService service(DiscountRepository discountRepository,
                                    DiscountUsageRepository usageRepository) {
        return new DiscountService(discountRepository, mock(DiscountMapper.class),
                mock(OrderRepository.class), usageRepository, mock(ApplicationEventPublisher.class),
                mock(CategoryService.class), mock(LocalizationService.class), mock(ProductService.class),
                mock(DealerService.class), mock(ProductVariantRepository.class));
    }

    private AppUser user(Long id) {
        AppUser user = new AppUser();
        user.setId(id);
        return user;
    }

    private Order order(Long id) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNumber("ORDER-" + id);
        order.setTotalAmount(BigDecimal.TEN);
        order.setOrderStatus(OrderStatus.PENDING);
        return order;
    }

    private record Fixture(DiscountService service, Discount discount,
                           DiscountRepository discountRepository,
                           DiscountUsageRepository usageRepository) {}

    private DiscountUsage usage(Discount discount) {
        DiscountUsage usage = new DiscountUsage();
        usage.setDiscount(discount);
        return usage;
    }
}
