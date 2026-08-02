package com.maxx_global.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxx_global.dto.notification.NotificationRequest;
import com.maxx_global.entity.AppUser;
import com.maxx_global.entity.Dealer;
import com.maxx_global.entity.Order;
import com.maxx_global.enums.CurrencyType;
import com.maxx_global.repository.AppUserRepository;
import com.maxx_global.service.AppUserService;
import com.maxx_global.service.LocalizationService;
import com.maxx_global.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEventServiceNullablePriceTest {
    @Mock NotificationService notificationService;
    @Mock AppUserService appUserService;
    @Mock AppUserRepository appUserRepository;
    @Mock LocalizationService localizationService;

    @Test
    void editRejectedNotificationUsesLocalizedUnavailablePriceAndEscapedJson() {
        when(appUserService.getUsersWithUserPermissions(anyList())).thenReturn(List.of(new AppUser()));
        when(localizationService.getCurrentRequestLocale()).thenReturn(Locale.forLanguageTag("tr"));
        when(localizationService.getMessage(eq("mail.price.unavailable"), any(Locale.class)))
                .thenReturn("Fiyat bilgisi bulunmuyor");

        Dealer dealer = new Dealer();
        dealer.setId(8L);
        dealer.setName("Bayi");
        AppUser customer = new AppUser();
        customer.setFirstName("Ada");
        customer.setLastName("Lovelace");
        customer.setDealer(dealer);
        Order order = new Order();
        order.setId(3L);
        order.setOrderNumber("ORDER-3");
        order.setUser(customer);
        order.setCurrency(CurrencyType.TRY);
        order.setTotalAmount(null);

        service().sendOrderEditRejectedNotification(order, "tırnaklı \"neden\"");

        ArgumentCaptor<NotificationRequest> request = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService).createNotification(request.capture(), anyList());
        assertTrue(request.getValue().data().contains("Fiyat bilgisi bulunmuyor"));
        assertTrue(request.getValue().data().contains("\\\"neden\\\""));
        assertFalse(request.getValue().data().contains("null TRY"));
    }

    private NotificationEventService service() {
        return new NotificationEventService(notificationService, appUserService, appUserRepository,
                localizationService, new ObjectMapper());
    }
}
