package com.maxx_global.service;

import com.maxx_global.dto.notification.*;
import com.maxx_global.entity.AppUser;
import com.maxx_global.entity.Notification;
import com.maxx_global.entity.NotificationRecipient;
import com.maxx_global.enums.EntityStatus;
import com.maxx_global.enums.Language;
import com.maxx_global.enums.NotificationStatus;
import com.maxx_global.enums.NotificationType;
import com.maxx_global.repository.AppUserRepository;
import com.maxx_global.repository.DealerRepository;
import com.maxx_global.repository.NotificationRecipientRepository;
import com.maxx_global.repository.NotificationRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
@Transactional
public class NotificationService {

    private static final Logger logger = Logger.getLogger(NotificationService.class.getName());

    private final NotificationRepository notificationRepository;
    private final NotificationRecipientRepository notificationRecipientRepository;
    private final AppUserRepository appUserRepository;
    private final DealerRepository dealerRepository;
    private final LocalizationService localizationService;

    public NotificationService(NotificationRepository notificationRepository,
                               NotificationRecipientRepository notificationRecipientRepository,
                               AppUserRepository appUserRepository,
                               DealerRepository dealerRepository,
                               LocalizationService localizationService) {
        this.notificationRepository = notificationRepository;
        this.notificationRecipientRepository = notificationRecipientRepository;
        this.appUserRepository = appUserRepository;
        this.dealerRepository = dealerRepository;
        this.localizationService = localizationService;
    }

    private String resolveLocalizedText(AppUser user, String textTr, String textEn) {
        Language language = localizationService.getLanguageForUser(user);
        if (language == Language.EN) {
            return isBlank(textEn) ? defaultString(textTr) : textEn;
        }
        return isBlank(textTr) ? defaultString(textEn) : textTr;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String defaultString(String value) {
        return value != null ? value : "";
    }

    private Notification createNotificationRecord(NotificationRequest request) {
        Notification notification = new Notification();
        notification.setTitle(defaultString(request.title()));
        notification.setTitleEn(defaultString(request.titleEn()));
        notification.setMessage(defaultString(request.message()));
        notification.setMessageEn(defaultString(request.messageEn()));
        notification.setType(request.type());
        notification.setRelatedEntityId(request.relatedEntityId());
        notification.setRelatedEntityType(request.relatedEntityType());
        notification.setPriority(request.priority() != null ? request.priority() : "MEDIUM");
        notification.setIcon(request.icon() != null ? request.icon() : getDefaultIcon(request.type()));
        notification.setActionUrl(request.actionUrl());
        notification.setData(request.data());
        return notificationRepository.save(notification);
    }

    private NotificationRecipient buildRecipient(Notification notification, AppUser user) {
        NotificationRecipient recipient = new NotificationRecipient();
        recipient.setNotification(notification);
        recipient.setUser(user);
        recipient.setNotificationStatus(NotificationStatus.UNREAD);
        return recipient;
    }

    private List<AppUser> filterEligibleUsers(List<AppUser> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }

        List<AppUser> eligible = new ArrayList<>();
        Set<Long> processed = new HashSet<>();

        for (AppUser candidate : candidates) {
            if (candidate != null && candidate.getId() != null && processed.add(candidate.getId())) {
                if (candidate.getStatus() == null || candidate.getStatus() == EntityStatus.ACTIVE) {
                    eligible.add(candidate);
                }
            }
        }

        return eligible;
    }

    public List<NotificationResponse> createNotification(NotificationRequest request) {
        return createNotification(request, Collections.emptyList());
    }

    public List<NotificationResponse> createNotification(NotificationRequest request, List<AppUser> explicitUsers) {
        List<AppUser> recipients;
        if (explicitUsers != null && !explicitUsers.isEmpty()) {
            recipients = filterEligibleUsers(explicitUsers);
        } else {
            if (request.dealerId() == null) {
                logger.warning("No dealer or users supplied for notification");
                return Collections.emptyList();
            }
            recipients = filterEligibleUsers(
                    appUserRepository.findByDealerIdAndStatusIs_Active(request.dealerId(), EntityStatus.ACTIVE)
            );
        }

        if (recipients.isEmpty()) {
            return Collections.emptyList();
        }

        Notification notification = createNotificationRecord(request);
        List<NotificationRecipient> recipientLinks = recipients.stream()
                .map(user -> buildRecipient(notification, user))
                .collect(Collectors.toList());
        notificationRecipientRepository.saveAll(recipientLinks);

        return recipientLinks.stream()
                .map(r -> NotificationResponse.fromRecipient(r, localizationService))
                .toList();
    }

    public void createNotificationByEvent(NotificationRequest request) {
        if (request.dealerId() == null) {
            throw new EntityNotFoundException("Kullanıcı bulunamadı: null");
        }
        AppUser user = appUserRepository.findById(request.dealerId())
                .orElseThrow(() -> new EntityNotFoundException("Kullanıcı bulunamadı: " + request.dealerId()));
        createNotification(request, List.of(user));
    }

    public void createNotificationForAdminByEvent(NotificationRequest request, List<AppUser> users) {
        createNotification(request, users);
    }

    public List<NotificationResponse> createNotificationForAllUsers(NotificationBroadcastRequest request) {
        List<AppUser> activeUsers = filterEligibleUsers(appUserRepository.findByStatus(EntityStatus.ACTIVE));
        Notification notification = createNotificationRecord(toRequest(null, request));
        List<NotificationRecipient> links = activeUsers.stream()
                .map(user -> buildRecipient(notification, user))
                .toList();
        notificationRecipientRepository.saveAll(links);
        return links.stream().map(r -> NotificationResponse.fromRecipient(r, localizationService)).toList();
    }

    public List<NotificationResponse> createNotificationForUsersByRole(String role, NotificationBroadcastRequest request) {
        List<AppUser> usersByRole = filterEligibleUsers(appUserRepository.findUsersByRole(role));
        Notification notification = createNotificationRecord(toRequest(null, request));
        List<NotificationRecipient> links = usersByRole.stream().map(u -> buildRecipient(notification, u)).toList();
        notificationRecipientRepository.saveAll(links);
        return links.stream().map(r -> NotificationResponse.fromRecipient(r, localizationService)).toList();
    }

    public List<NotificationResponse> createNotificationForSpecificUsers(List<Long> userIds, NotificationBroadcastRequest request) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<AppUser> users = filterEligibleUsers(appUserRepository.findAllById(userIds));
        Notification notification = createNotificationRecord(toRequest(null, request));
        List<NotificationRecipient> links = users.stream().map(u -> buildRecipient(notification, u)).toList();
        notificationRecipientRepository.saveAll(links);
        return links.stream().map(r -> NotificationResponse.fromRecipient(r, localizationService)).toList();
    }

    public List<NotificationResponse> createNotificationForMultipleDealers(List<Long> dealerIds, NotificationBroadcastRequest request) {
        if (dealerIds == null || dealerIds.isEmpty()) {
            throw new IllegalArgumentException("En az bir dealer ID'si gereklidir");
        }
        List<AppUser> recipients = new ArrayList<>();
        for (Long dealerId : dealerIds) {
            dealerRepository.findById(dealerId)
                    .orElseThrow(() -> new EntityNotFoundException("Dealer bulunamadı: " + dealerId));
            recipients.addAll(appUserRepository.findByDealerIdAndStatusIs_Active(dealerId, EntityStatus.ACTIVE));
        }
        return createNotification(toRequest(null, request), recipients);
    }

    public List<NotificationResponse> createNotificationForMultipleDealers(List<Long> dealerIds, NotificationRequest request) {
        if (dealerIds == null || dealerIds.isEmpty()) {
            throw new IllegalArgumentException("En az bir dealer ID'si gereklidir");
        }
        List<AppUser> recipients = new ArrayList<>();
        for (Long dealerId : dealerIds) {
            dealerRepository.findById(dealerId)
                    .orElseThrow(() -> new EntityNotFoundException("Dealer bulunamadı: " + dealerId));
            recipients.addAll(appUserRepository.findByDealerIdAndStatusIs_Active(dealerId, EntityStatus.ACTIVE));
        }
        return createNotification(request, recipients);
    }

    private NotificationRequest toRequest(Long dealerId, NotificationBroadcastRequest request) {
        return new NotificationRequest(
                dealerId,
                request.title(),
                request.titleEn(),
                request.message(),
                request.messageEn(),
                request.type(),
                request.relatedEntityId(),
                request.relatedEntityType(),
                request.priority(),
                request.icon(),
                request.actionUrl(),
                request.data()
        );
    }

    public Page<NotificationResponse> getUserNotifications(Long userId, int page, int size, NotificationFilterRequest filter) {
        Pageable pageable = PageRequest.of(page, size);
        Page<NotificationRecipient> recipients = getFilteredRecipients(userId, filter, pageable);
        return recipients.map(r -> NotificationResponse.fromRecipient(r, localizationService));
    }

    private Page<NotificationRecipient> getFilteredRecipients(Long userId, NotificationFilterRequest filter, Pageable pageable) {
        if (filter != null) {
            if (filter.unreadOnly()) {
                return notificationRecipientRepository.findByUserIdAndNotificationStatusOrderByCreatedAtDesc(
                        userId, NotificationStatus.UNREAD, pageable);
            }
            if (filter.notificationStatus() != null) {
                return notificationRecipientRepository.findByUserIdAndNotificationStatusOrderByCreatedAtDesc(
                        userId, filter.notificationStatus(), pageable);
            }
            if (filter.type() != null) {
                return notificationRecipientRepository.findByUserIdAndNotificationNotificationTypeOrderByCreatedAtDesc(
                        userId, filter.type(), pageable);
            }
            if (filter.priority() != null) {
                return notificationRecipientRepository.findByUserIdAndNotificationPriorityOrderByCreatedAtDesc(
                        userId, filter.priority(), pageable);
            }
        }
        return notificationRecipientRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    public long getUnreadCount(Long userId) {
        return notificationRecipientRepository.countByUserIdAndNotificationStatus(userId, NotificationStatus.UNREAD);
    }

    public List<NotificationResponse> getRecentNotifications(Long userId, int limit) {
        Page<NotificationRecipient> page = notificationRecipientRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit));
        return page.getContent().stream()
                .map(r -> NotificationResponse.fromRecipient(r, localizationService))
                .toList();
    }

    public NotificationSummary getUserNotificationSummary(Long userId) {
        long unread = getUnreadCount(userId);
        return new NotificationSummary(unread);
    }

    public NotificationResponse markAsRead(Long notificationId, Long currentUserId) {
        NotificationRecipient recipient = notificationRecipientRepository
                .findByNotificationIdAndUserId(notificationId, currentUserId)
                .orElseThrow(() -> new EntityNotFoundException("Bildirim bulunamadı: " + notificationId));
        if (!recipient.isRead()) {
            recipient.setNotificationStatus(NotificationStatus.READ);
            recipient.setReadAt(LocalDateTime.now());
            notificationRecipientRepository.save(recipient);
        }
        return NotificationResponse.fromRecipient(recipient, localizationService);
    }

    public void markAllAsRead(Long userId) {
        notificationRecipientRepository.markAllAsRead(userId, NotificationStatus.READ, LocalDateTime.now(), NotificationStatus.UNREAD);
    }

    public void bulkUpdateStatus(List<Long> notificationIds, Long userId, NotificationStatusUpdateRequest request) {
        LocalDateTime readAt = (request.notificationStatus() == NotificationStatus.READ ||
                request.notificationStatus() == NotificationStatus.ARCHIVED) ? LocalDateTime.now() : null;
        notificationRecipientRepository.bulkUpdateStatus(notificationIds, userId, request.notificationStatus(), readAt);
    }

    public void deleteNotification(Long notificationId, Long currentUserId) {
        NotificationRecipient recipient = notificationRecipientRepository
                .findByNotificationIdAndUserId(notificationId, currentUserId)
                .orElseThrow(() -> new EntityNotFoundException("Bildirim bulunamadı: " + notificationId));
        notificationRecipientRepository.delete(recipient);
    }

    private String getDefaultIcon(NotificationType type) {
        if (type == null) {
            return "bell";
        }
        return switch (type) {
            case ORDER_CREATED -> "shopping-cart";
            case ORDER_APPROVED -> "check-circle";
            case DISCOUNT_CREATED, DISCOUNT_UPDATED, DISCOUNT_EXPIRED -> "tag";
            default -> "bell";
        };
    }

    public NotificationBroadcastStatsResponse getBroadcastStats(String timeRange) {
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = switch (timeRange == null ? "week" : timeRange.toLowerCase()) {
            case "day" -> endDate.minusDays(1);
            case "month" -> endDate.minusMonths(1);
            case "week" -> endDate.minusWeeks(1);
            default -> throw new IllegalArgumentException("Unsupported time range: " + timeRange);
        };

        long totalNotifications = notificationRepository.countByCreatedAtAfter(startDate);
        Map<NotificationType, Long> byType = notificationRepository.countNotificationsByTypeAfter(startDate);
        Map<String, Long> byPriority = notificationRepository.countNotificationsByPriorityAfter(startDate);

        long totalRecipients = notificationRecipientRepository.countByCreatedAtBetween(startDate, endDate);
        long readRecipients = notificationRecipientRepository.countByStatusBetween(
                startDate,
                endDate,
                List.of(NotificationStatus.READ, NotificationStatus.ARCHIVED)
        );
        long distinctUsers = notificationRecipientRepository.countDistinctUsersBetween(startDate, endDate);

        double readRate = totalRecipients == 0 ? 0.0 : (double) readRecipients / totalRecipients;

        return new NotificationBroadcastStatsResponse(
                totalNotifications,
                distinctUsers,
                byType,
                byPriority,
                readRate,
                startDate,
                endDate
        );
    }
}
