package com.maxx_global.service;

import com.maxx_global.dto.discount.DiscountMapper;
import com.maxx_global.entity.Discount;
import com.maxx_global.entity.DiscountUsage;
import com.maxx_global.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

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

    private DiscountUsage usage(Discount discount) {
        DiscountUsage usage = new DiscountUsage();
        usage.setDiscount(discount);
        return usage;
    }
}
