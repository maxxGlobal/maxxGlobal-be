package com.maxx_global.controller;

import com.maxx_global.dto.BaseResponse;
import com.maxx_global.entity.Discount;
import com.maxx_global.event.DiscountCreatedEvent;
import com.maxx_global.event.DiscountExpiredEvent;
import com.maxx_global.event.DiscountSoonExpiringEvent;
import com.maxx_global.event.DiscountUpdatedEvent;
import com.maxx_global.jobs.DiscountExpiryJob;
import com.maxx_global.security.CustomUserDetails;
import com.maxx_global.service.DiscountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api/test")
public class TestController {

    private static final Logger logger = Logger.getLogger(TestController.class.getName());

    private final DiscountService discountService;
    private final ApplicationEventPublisher eventPublisher;
    private final DiscountExpiryJob discountExpiryJob;

    public TestController(DiscountService discountService, ApplicationEventPublisher eventPublisher, DiscountExpiryJob discountExpiryJob) {
        this.discountService = discountService;
        this.eventPublisher = eventPublisher;
        this.discountExpiryJob = discountExpiryJob;
    }

    @GetMapping("/public")
    public ResponseEntity<String> publicEndpoint() {
        return ResponseEntity.ok("Bu endpoint herkese açık - Token gerektirmez");
    }

    @GetMapping("/protected")
    public ResponseEntity<String> protectedEndpoint() {
        return ResponseEntity.ok("Bu endpoint token gerektiriyor - Başarılı!");
    }

    @GetMapping("/user-info")
    public ResponseEntity<Map<String, Object>> getUserInfo(Authentication authentication) {
        Map<String, Object> userInfo = new HashMap<>();

        if (authentication.getPrincipal() instanceof CustomUserDetails) {
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            userInfo.put("userId", userDetails.getId());
            userInfo.put("email", userDetails.getUsername());
            userInfo.put("authorities", userDetails.getAuthorities());
        }

        userInfo.put("authenticated", authentication.isAuthenticated());
        userInfo.put("principal", authentication.getName());

        return ResponseEntity.ok(userInfo);
    }

    @GetMapping("/aaaa")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    public String test() {
        return "ok";
    }

    @PostMapping("/created/{discountId}")
    @Operation(
            summary = "Test - İndirim oluşturuldu bildirimi",
            description = "Belirtilen indirim için 'oluşturuldu' bildirimi gönderir (test amaçlı)"
    )
    public ResponseEntity<BaseResponse<String>> testDiscountCreatedNotification(
            @Parameter(description = "İndirim ID'si", example = "1", required = true)
            @PathVariable Long discountId) {

        try {
            logger.info("Testing discount created notification for discount: " + discountId);

            Discount discount = discountService.getDiscountEntityById(discountId);
            DiscountCreatedEvent event = new DiscountCreatedEvent(discount);
            eventPublisher.publishEvent(event);

            return ResponseEntity.ok(BaseResponse.success(
                    "Discount created notification test completed for: " + discount.getName()));

        } catch (Exception e) {
            logger.severe("Error testing discount created notification: " + e.getMessage());
            return ResponseEntity.badRequest()
                    .body(BaseResponse.error("Test failed: " + e.getMessage(), 400));
        }
    }

    @PostMapping("/updated/{discountId}")
    @Operation(
            summary = "Test - İndirim güncellendi bildirimi",
            description = "Belirtilen indirim için 'güncellendi' bildirimi gönderir (test amaçlı)"
    )
    public ResponseEntity<BaseResponse<String>> testDiscountUpdatedNotification(
            @Parameter(description = "İndirim ID'si", example = "1", required = true)
            @PathVariable Long discountId,
            @Parameter(description = "Aktivasyon değişti mi?", example = "true")
            @RequestParam(defaultValue = "true") boolean activationChanged,
            @Parameter(description = "Tarih değişti mi?", example = "false")
            @RequestParam(defaultValue = "false") boolean dateChanged) {

        try {
            logger.info("Testing discount updated notification for discount: " + discountId);

            Discount discount = discountService.getDiscountEntityById(discountId);
            DiscountUpdatedEvent event = new DiscountUpdatedEvent(
                    discount, discount, activationChanged, dateChanged);
            eventPublisher.publishEvent(event);

            return ResponseEntity.ok(BaseResponse.success(
                    "Discount updated notification test completed for: " + discount.getName()));

        } catch (Exception e) {
            logger.severe("Error testing discount updated notification: " + e.getMessage());
            return ResponseEntity.badRequest()
                    .body(BaseResponse.error("Test failed: " + e.getMessage(), 400));
        }
    }

    @PostMapping("/expired/{discountId}")
    @Operation(
            summary = "Test - İndirim süresi doldu bildirimi",
            description = "Belirtilen indirim için 'süresi doldu' bildirimi gönderir (test amaçlı)"
    )
    public ResponseEntity<BaseResponse<String>> testDiscountExpiredNotification(
            @Parameter(description = "İndirim ID'si", example = "1", required = true)
            @PathVariable Long discountId) {

        try {
            logger.info("Testing discount expired notification for discount: " + discountId);

            Discount discount = discountService.getDiscountEntityById(discountId);
            DiscountExpiredEvent event = new DiscountExpiredEvent(discount);
            eventPublisher.publishEvent(event);

            return ResponseEntity.ok(BaseResponse.success(
                    "Discount expired notification test completed for: " + discount.getName()));

        } catch (Exception e) {
            logger.severe("Error testing discount expired notification: " + e.getMessage());
            return ResponseEntity.badRequest()
                    .body(BaseResponse.error("Test failed: " + e.getMessage(), 400));
        }
    }

    @PostMapping("/soon-expiring/{discountId}")
    @Operation(
            summary = "Test - İndirim yakında sona erecek bildirimi",
            description = "Belirtilen indirim için 'yakında sona erecek' bildirimi gönderir (test amaçlı)"
    )
    public ResponseEntity<BaseResponse<String>> testDiscountSoonExpiringNotification(
            @Parameter(description = "İndirim ID'si", example = "1", required = true)
            @PathVariable Long discountId,
            @Parameter(description = "Kaç gün kaldı", example = "3")
            @RequestParam(defaultValue = "3") int daysUntilExpiration) {

        try {
            logger.info("Testing discount soon expiring notification for discount: " + discountId);

            Discount discount = discountService.getDiscountEntityById(discountId);
            DiscountSoonExpiringEvent event = new DiscountSoonExpiringEvent(
                    discount, daysUntilExpiration);
            eventPublisher.publishEvent(event);

            return ResponseEntity.ok(BaseResponse.success(
                    "Discount soon expiring notification test completed for: " + discount.getName()));

        } catch (Exception e) {
            logger.severe("Error testing discount soon expiring notification: " + e.getMessage());
            return ResponseEntity.badRequest()
                    .body(BaseResponse.error("Test failed: " + e.getMessage(), 400));
        }
    }

    @PostMapping("/run-expiry-job")
    @Operation(
            summary = "Test - Süresi dolan indirimler job'ını çalıştır",
            description = "Manuel olarak süresi dolan indirimleri kontrol eden job'ı çalıştırır (test amaçlı)"
    )
    public ResponseEntity<BaseResponse<String>> runExpiryJobManually() {
        try {
            logger.info("Running discount expiry job manually for testing");

            discountExpiryJob.runExpiryCheckManually();

            return ResponseEntity.ok(BaseResponse.success(
                    "Discount expiry job completed successfully"));

        } catch (Exception e) {
            logger.severe("Error running discount expiry job: " + e.getMessage());
            return ResponseEntity.badRequest()
                    .body(BaseResponse.error("Job failed: " + e.getMessage(), 400));
        }
    }

    @GetMapping("/expiry-job-status")
    @Operation(
            summary = "Süresi dolan indirimler job durumu",
            description = "Süresi dolan indirimleri kontrol eden job'ın durumunu getirir"
    )
    public ResponseEntity<BaseResponse<Object>> getExpiryJobStatus() {
        try {
            Object status = discountExpiryJob.getExpiryJobStatus();
            return ResponseEntity.ok(BaseResponse.success(status));

        } catch (Exception e) {
            logger.severe("Error getting expiry job status: " + e.getMessage());
            return ResponseEntity.badRequest()
                    .body(BaseResponse.error("Status check failed: " + e.getMessage(), 400));
        }
    }

    @GetMapping("/expiry-statistics")
    @Operation(
            summary = "İndirim süresi dolma istatistikleri",
            description = "İndirim süresi dolma istatistiklerini getirir"
    )
    public ResponseEntity<BaseResponse<Object>> getExpiryStatistics() {
        try {
            Object statistics = discountExpiryJob.getExpiryStatistics();
            return ResponseEntity.ok(BaseResponse.success(statistics));

        } catch (Exception e) {
            logger.severe("Error getting expiry statistics: " + e.getMessage());
            return ResponseEntity.badRequest()
                    .body(BaseResponse.error("Statistics failed: " + e.getMessage(), 400));
        }
    }
}