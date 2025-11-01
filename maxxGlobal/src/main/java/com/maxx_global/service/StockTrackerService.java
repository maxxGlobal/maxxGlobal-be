package com.maxx_global.service;

import com.maxx_global.dto.stock.*;
import com.maxx_global.entity.*;
import com.maxx_global.enums.EntityStatus;
import com.maxx_global.enums.StockMovementType;
import com.maxx_global.repository.StockMovementRepository;
import com.maxx_global.repository.ProductRepository;
import com.maxx_global.repository.ProductVariantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class StockTrackerService {

    private static final Logger logger = Logger.getLogger(StockTrackerService.class.getName());

    private final StockMovementRepository stockMovementRepository;
    private final ProductRepository productRepository;
    private final StockMovementMapper stockMovementMapper;

    public StockTrackerService(StockMovementRepository stockMovementRepository,
                                ProductRepository productRepository,
                                StockMovementMapper stockMovementMapper) {
        this.stockMovementRepository = stockMovementRepository;
        this.productRepository = productRepository;
        this.stockMovementMapper = stockMovementMapper;
    }

    // ==================== BASIC CRUD OPERATIONS ====================

    public StockMovementResponse getStockMovementById(Long id) {
        logger.info("Fetching stock movement with id: " + id);

        StockMovement stockMovement = stockMovementRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Stok hareketi bulunamadı: " + id));

        return stockMovementMapper.toDto(stockMovement);
    }

    public Page<StockMovementResponse> getAllStockMovements(int page, int size, String sortBy, String sortDirection,
                                                            String movementType, Long productId, String startDate, String endDate) {
        logger.info("Fetching all stock movements with filters");

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        // Farklı senaryolar için farklı repository metodları kullan
        Page<StockMovement> movements;

        try {
            if (startDate != null && endDate != null && movementType != null && productId != null) {
                // Tüm filtreler var
                LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
                LocalDateTime end = LocalDate.parse(endDate).atTime(23, 59, 59);
                StockMovementType type = StockMovementType.valueOf(movementType.toUpperCase());
                movements = stockMovementRepository.findByMovementTypeAndProductIdAndMovementDateBetweenAndStatus(
                        type, productId, start, end, EntityStatus.ACTIVE, pageable);

            } else if (movementType != null && productId != null) {
                // Hareket tipi ve ürün filtresi
                StockMovementType type = StockMovementType.valueOf(movementType.toUpperCase());
                movements = stockMovementRepository.findByMovementTypeAndProductIdAndStatus(
                        type, productId, EntityStatus.ACTIVE, pageable);

            } else if (startDate != null && endDate != null && productId != null) {
                // Sadece tarih filtresi
                LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
                LocalDateTime end = LocalDate.parse(endDate).atTime(23, 59, 59);
                movements = stockMovementRepository.findByMovementDateBetweenAndProductIdAndStatus(
                        start, end,productId, EntityStatus.ACTIVE, pageable);

            } else if (movementType != null) {
                // Sadece hareket tipi filtresi
                StockMovementType type = StockMovementType.valueOf(movementType.toUpperCase());
                movements = stockMovementRepository.findByMovementTypeAndStatus(
                        type, EntityStatus.ACTIVE, pageable);

            } else if (productId != null) {
                // Sadece ürün filtresi
                movements = stockMovementRepository.findByProductIdAndStatus(
                        productId, EntityStatus.ACTIVE, pageable);

            } else {
                // Hiç filtre yok
                movements = stockMovementRepository.findByStatus(EntityStatus.ACTIVE, pageable);
            }

        } catch (Exception e) {
            logger.severe("Error in stock movements query: " + e.getMessage());
            throw new RuntimeException("Stok hareketleri getirilirken hata: " + e.getMessage());
        }

        return movements.map(stockMovementMapper::toDto);
    }

    public Page<StockMovementResponse> getStockMovementsByProduct(Long productId, int page, int size, String sortDirection) {
        logger.info("Fetching stock movements for product: " + productId);

        // Ürün varlık kontrolü
        productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Ürün bulunamadı: " + productId));

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), "movementDate");
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<StockMovement> movements = stockMovementRepository.findByProductIdAndStatus(
                productId, EntityStatus.ACTIVE, pageable);

        return movements.map(stockMovementMapper::toDto);
    }

    public List<StockMovementResponse> getRecentStockMovements(int limit) {
        logger.info("Fetching recent stock movements, limit: " + limit);

        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "movementDate"));
        Page<StockMovement> movements = stockMovementRepository.findByStatus(EntityStatus.ACTIVE, pageable);

        return movements.getContent().stream()
                .map(stockMovementMapper::toDto)
                .collect(Collectors.toList());
    }

    // ==================== STOCK SUMMARY OPERATIONS ====================

    public StockSummaryResponse getProductStockSummary(Long productId) {
        logger.info("Fetching stock summary for product: " + productId);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Ürün bulunamadı: " + productId));

        List<StockMovement> movements = stockMovementRepository.findByProductIdAndStatusOrderByMovementDateDesc(
                productId, EntityStatus.ACTIVE);

        // Toplam giriş ve çıkış hesapla
        Integer totalStockIn = movements.stream()
                .filter(m -> isStockInMovement(m.getMovementType()))
                .mapToInt(StockMovement::getQuantity)
                .sum();

        Integer totalStockOut = movements.stream()
                .filter(m -> isStockOutMovement(m.getMovementType()))
                .mapToInt(StockMovement::getQuantity)
                .sum();

        // Ortalama maliyet hesapla (eğer maliyet bilgisi varsa)
        BigDecimal averageCost = movements.stream()
                .filter(m -> m.getUnitCost() != null)
                .map(StockMovement::getUnitCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(Math.max(1, movements.size())), 2, BigDecimal.ROUND_HALF_UP);

        // Toplam değer hesapla
        BigDecimal totalValue = averageCost.multiply(BigDecimal.valueOf(product.getStockQuantity()));

        // Son hareket bilgisi
        LocalDateTime lastMovementDate = movements.isEmpty() ? null : movements.get(0).getMovementDate();
        String lastMovementType = movements.isEmpty() ? null : movements.get(0).getMovementType().getDisplayName();

        return new StockSummaryResponse(
                product.getId(),
                product.getName(),
                product.getCode(),
                product.getStockQuantity(),
                totalStockIn,
                totalStockOut,
                averageCost,
                totalValue,
                lastMovementDate,
                lastMovementType
        );
    }

    public Page<StockSummaryResponse> getAllProductsStockSummary(int page, int size, String sortBy, String sortDirection) {
        logger.info("Fetching stock summary for all products");

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()),
                sortBy.equals("currentStock") ? "stockQuantity" : sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Product> products = productRepository.findByStatus(EntityStatus.ACTIVE, pageable);

        return products.map(product -> {
            // Her ürün için özet bilgi oluştur (performans için basit versiyonu)
            return new StockSummaryResponse(
                    product.getId(),
                    product.getName(),
                    product.getCode(),
                    product.getStockQuantity(),
                    null, // Toplu sorgulamada detay hesaplamayı atla
                    null,
                    null,
                    null,
                    null,
                    null
            );
        });
    }

    // ==================== SEARCH AND FILTER OPERATIONS ====================

    public Page<StockMovementResponse> searchStockMovements(String searchTerm, int page, int size, String sortDirection) {
        logger.info("Searching stock movements with term: " + searchTerm);

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), "movementDate");
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<StockMovement> movements = stockMovementRepository.searchMovements(searchTerm, EntityStatus.ACTIVE, pageable);

        return movements.map(stockMovementMapper::toDto);
    }

    @Transactional
    public void trackInitialStock(Product product, Integer initialStock, AppUser performedBy) {
        logger.info("Tracking initial stock for product: " + product.getCode() +
                ", initial stock: " + initialStock);

        if (initialStock == null || initialStock <= 0) {
            return;
        }

        String reason = "Ürün için başlangıç stoku belirlendi: " + initialStock;

        trackStockChange(product, 0, initialStock, StockMovementType.INITIAL_STOCK,
                reason, performedBy, "INITIAL", null);
    }

    /**
     * Variant için başlangıç stok takibi
     * ProductVariant oluşturulduğunda bu metod çağrılır
     */
    @Transactional
    public void trackInitialStockForVariant(ProductVariant variant, Integer initialStock, AppUser performedBy) {
        logger.info("Tracking initial stock for variant: " + variant.getSku() +
                " (Product: " + variant.getProduct().getCode() + ")" +
                ", initial stock: " + initialStock);

        if (initialStock == null || initialStock <= 0) {
            return;
        }

        try {
            String reason = String.format("Variant başlangıç stoku - Boyut: %s, SKU: %s, Miktar: %d",
                    variant.getSize(), variant.getSku(), initialStock);

            // StockMovement oluştur
            StockMovement stockMovement = new StockMovement();
            stockMovement.setProduct(variant.getProduct());
            stockMovement.setProductVariant(variant); // ✅ Variant bilgisini ekle
            stockMovement.setMovementType(StockMovementType.INITIAL_STOCK);
            stockMovement.setQuantity(initialStock);
            stockMovement.setPreviousStock(0);
            stockMovement.setNewStock(initialStock);
            stockMovement.setMovementDate(LocalDateTime.now());
            stockMovement.setPerformedBy(performedBy != null ? performedBy.getId() : null);
            stockMovement.setReferenceType("VARIANT_INITIAL");
            stockMovement.setReferenceId(variant.getId());
            stockMovement.setNotes(reason);
            stockMovement.setStatus(EntityStatus.ACTIVE);

            stockMovementRepository.save(stockMovement);

            logger.info("Initial stock movement created for variant: " + variant.getSku() +
                    ", Quantity=" + initialStock);

        } catch (Exception e) {
            logger.severe("Error creating initial stock movement for variant " + variant.getSku() +
                    ": " + e.getMessage());
            throw new RuntimeException("Variant stok takibi oluşturulamadı: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void trackOrderReservation(Product product, Integer reservedQuantity,
                                      AppUser user, String orderNumber, Long orderId) {

        Integer currentStock = product.getStockQuantity();
        Integer newStock = Math.max(0, currentStock - reservedQuantity);

        String reason = "Sipariş rezervasyonu - Sipariş No: " + orderNumber +
                " (Rezerve: " + reservedQuantity + ")";

        trackStockChange(product, currentStock, newStock, StockMovementType.ORDER_RESERVED,
                reason, user, "ORDER", orderId);
    }

    @Transactional
    public void trackOrderCancellation(Product product, Integer returnedQuantity,
                                       AppUser user, String orderNumber, Long orderId) {

        Integer currentStock = product.getStockQuantity();
        Integer newStock = currentStock + returnedQuantity;

        String reason = "İptal edilen sipariş iadesi - Sipariş No: " + orderNumber +
                " (İade: " + returnedQuantity + ")";

        trackStockChange(product, currentStock, newStock, StockMovementType.ORDER_CANCELLED_RETURN,
                reason, user, "ORDER_CANCELLATION", orderId);
    }

    @Transactional
    public void createMovementForOrder(Order order, boolean isReservation) {
        logger.info("Creating stock movements for order: " + order.getOrderNumber() +
                ", reservation: " + isReservation);

        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            Integer quantity = item.getQuantity();

            StockMovementType movementType = isReservation ?
                    StockMovementType.ORDER_RESERVED : StockMovementType.ORDER_CANCELLED_RETURN;

            Integer currentStock = product.getStockQuantity();
            Integer newStock = isReservation ?
                    Math.max(0, currentStock - quantity) : currentStock + quantity;

            String notes = String.format("%s - Sipariş No: %s, Miktar: %d",
                    isReservation ? "Sipariş rezervasyonu" : "İptal iadesi",
                    order.getOrderNumber(), quantity);

            StockMovement movement = new StockMovement();
            movement.setProduct(product);
            movement.setMovementType(movementType);
            movement.setQuantity(quantity);
            movement.setPreviousStock(currentStock);
            movement.setNewStock(newStock);
            movement.setMovementDate(LocalDateTime.now());
            movement.setPerformedBy(order.getUser().getId());
            movement.setReferenceType("ORDER");
            movement.setReferenceId(order.getId());
            movement.setNotes(notes);
            movement.setStatus(EntityStatus.ACTIVE);

            stockMovementRepository.save(movement);

            // Ürün stokunu güncelle
            product.setStockQuantity(newStock);
            productRepository.save(product);
        }

        logger.info("Order stock movements created successfully");
    }

    @Transactional
    public void trackStockChange(Product product, Integer oldStock, Integer newStock,
                                 StockMovementType movementType, String reason,
                                 AppUser performedBy, String referenceType, Long referenceId) {

        if (oldStock == null) oldStock = 0;
        if (newStock == null) newStock = 0;

        if (oldStock.equals(newStock)) {
            logger.info("No stock change detected for product: " + product.getCode());
            return;
        }

        try {
            Integer stockDifference = newStock - oldStock;

            StockMovement stockMovement = new StockMovement();
            stockMovement.setProduct(product);
            stockMovement.setMovementType(movementType);
            stockMovement.setQuantity(Math.abs(stockDifference));
            stockMovement.setPreviousStock(oldStock);
            stockMovement.setNewStock(newStock);
            stockMovement.setMovementDate(LocalDateTime.now());
            stockMovement.setPerformedBy(performedBy != null ? performedBy.getId() : null);
            stockMovement.setReferenceType(referenceType);
            stockMovement.setReferenceId(referenceId);
            stockMovement.setNotes(reason);
            stockMovement.setStatus(EntityStatus.ACTIVE);

            stockMovementRepository.save(stockMovement);

            logger.info("Stock movement created: Product=" + product.getCode() +
                    ", Type=" + movementType.getDisplayName() +
                    ", Quantity=" + Math.abs(stockDifference) +
                    ", Old=" + oldStock + ", New=" + newStock);

        } catch (Exception e) {
            logger.severe("Error creating stock movement for product " + product.getCode() +
                    ": " + e.getMessage());
        }
    }

    public List<StockMovementResponse> getStockMovementsByReference(String referenceType, Long referenceId) {
        logger.info("Fetching stock movements by reference: " + referenceType + "/" + referenceId);

        List<StockMovement> movements = stockMovementRepository.findByReferenceTypeAndReferenceIdAndStatusOrderByMovementDateDesc(
                referenceType, referenceId, EntityStatus.ACTIVE);

        return movements.stream()
                .map(stockMovementMapper::toDto)
                .collect(Collectors.toList());
    }

    // ==================== ANALYTICS AND REPORTING ====================

    public Map<String, Object> getMovementTypeStatistics(String startDate, String endDate) {
        logger.info("Fetching movement type statistics for period: " + startDate + " to " + endDate);

        LocalDateTime start = startDate != null ? LocalDate.parse(startDate).atStartOfDay() : LocalDateTime.now().minusMonths(1);
        LocalDateTime end = endDate != null ? LocalDate.parse(endDate).atTime(23, 59, 59) : LocalDateTime.now();

        List<StockMovement> movements = stockMovementRepository.findByMovementDateBetweenAndStatus(start, end, EntityStatus.ACTIVE);

        Map<StockMovementType, Long> typeCount = movements.stream()
                .collect(Collectors.groupingBy(StockMovement::getMovementType, Collectors.counting()));

        Map<StockMovementType, Integer> typeQuantity = movements.stream()
                .collect(Collectors.groupingBy(StockMovement::getMovementType,
                        Collectors.summingInt(StockMovement::getQuantity)));

        Map<String, Object> result = new HashMap<>();
        result.put("period", Map.of("start", start.toLocalDate(), "end", end.toLocalDate()));
        result.put("totalMovements", movements.size());
        result.put("movementCounts", typeCount);
        result.put("movementQuantities", typeQuantity);

        return result;
    }

    public Map<String, Object> getDailyStockSummary(String reportDate) {
        logger.info("Fetching daily stock summary for date: " + reportDate);

        LocalDate targetDate = reportDate != null ? LocalDate.parse(reportDate) : LocalDate.now();
        LocalDateTime start = targetDate.atStartOfDay();
        LocalDateTime end = targetDate.atTime(23, 59, 59);

        List<StockMovement> movements = stockMovementRepository.findByMovementDateBetweenAndStatus(start, end, EntityStatus.ACTIVE);

        Integer totalInQuantity = movements.stream()
                .filter(m -> isStockInMovement(m.getMovementType()))
                .mapToInt(StockMovement::getQuantity)
                .sum();

        Integer totalOutQuantity = movements.stream()
                .filter(m -> isStockOutMovement(m.getMovementType()))
                .mapToInt(StockMovement::getQuantity)
                .sum();

        Map<String, Object> result = new HashMap<>();
        result.put("date", targetDate);
        result.put("totalMovements", movements.size());
        result.put("totalStockIn", totalInQuantity);
        result.put("totalStockOut", totalOutQuantity);
        result.put("netChange", totalInQuantity - totalOutQuantity);

        return result;
    }

    public List<Map<String, Object>> getTopMovementProducts(String startDate, String endDate, int limit) {
        logger.info("Fetching top movement products for period: " + startDate + " to " + endDate);

        LocalDateTime start = startDate != null ? LocalDate.parse(startDate).atStartOfDay() : LocalDateTime.now().minusMonths(1);
        LocalDateTime end = endDate != null ? LocalDate.parse(endDate).atTime(23, 59, 59) : LocalDateTime.now();

        List<StockMovement> movements = stockMovementRepository.findByMovementDateBetweenAndStatus(start, end, EntityStatus.ACTIVE);

        return movements.stream()
                .collect(Collectors.groupingBy(m -> m.getProduct().getId(),
                        Collectors.collectingAndThen(Collectors.toList(),
                                list -> {
                                    Product product = list.get(0).getProduct();
                                    int totalQuantity = list.stream().mapToInt(StockMovement::getQuantity).sum();
                                    Map<String, Object> result = new HashMap<>();
                                    result.put("productId", product.getId());
                                    result.put("productName", product.getName());
                                    result.put("productCode", product.getCode());
                                    result.put("totalMovements", list.size());
                                    result.put("totalQuantity", totalQuantity);
                                    return result;
                                })))
                .values()
                .stream()
                .sorted((a, b) -> Integer.compare((Integer) b.get("totalQuantity"), (Integer) a.get("totalQuantity")))
                .limit(limit)
                .collect(Collectors.toList());
    }

    // ==================== STOCK COUNT OPERATIONS ====================

    @Transactional
    public StockMovementResponse recordStockCount(StockCountRequest request) {
        logger.info("Recording stock count for product: " + request.productId() +
                ", counted quantity: " + request.countedQuantity());

        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new EntityNotFoundException("Ürün bulunamadı: " + request.productId()));

        Integer systemStock = product.getStockQuantity();
        Integer countedStock = request.countedQuantity();
        Integer difference = countedStock - systemStock;

        if (difference == 0) {
            // Fark yok, sadece sayım kaydı oluştur
            StockMovement countRecord = new StockMovement();
            countRecord.setProduct(product);
            countRecord.setMovementType(StockMovementType.STOCK_COUNT);
            countRecord.setQuantity(countedStock);
            countRecord.setPreviousStock(systemStock);
            countRecord.setNewStock(countedStock);
            countRecord.setMovementDate(LocalDateTime.now());
            countRecord.setDocumentNumber(request.documentNumber());
            countRecord.setNotes("Stok sayımı - fark yok: " + request.notes());
            countRecord.setStatus(EntityStatus.ACTIVE);

            StockMovement saved = stockMovementRepository.save(countRecord);
            return stockMovementMapper.toDto(saved);
        }

        // Fark var, düzeltme hareketi oluştur ve stoku güncelle
        StockMovementType correctionType = difference > 0 ?
                StockMovementType.ADJUSTMENT_IN : StockMovementType.ADJUSTMENT_OUT;

        StockMovement correction = new StockMovement();
        correction.setProduct(product);
        correction.setMovementType(correctionType);
        correction.setQuantity(Math.abs(difference));
        correction.setPreviousStock(systemStock);
        correction.setNewStock(countedStock);
        correction.setMovementDate(LocalDateTime.now());
        correction.setDocumentNumber(request.documentNumber());
        correction.setNotes("Sayım düzeltmesi (Sistem: " + systemStock + ", Sayılan: " + countedStock + "): " + request.notes());
        correction.setStatus(EntityStatus.ACTIVE);

        // Ürün stokunu güncelle
        product.setStockQuantity(countedStock);
        productRepository.save(product);

        StockMovement saved = stockMovementRepository.save(correction);
        logger.info("Stock count correction recorded: " + difference + " difference for product: " + product.getCode());

        return stockMovementMapper.toDto(saved);
    }

    // ==================== UTILITY METHODS ====================

    private boolean isStockInMovement(StockMovementType type) {
        return type == StockMovementType.STOCK_IN ||
                type == StockMovementType.ADJUSTMENT_IN ||
                type == StockMovementType.ORDER_CANCELLED_RETURN ||
                type == StockMovementType.EXCEL_IMPORT ||
                type == StockMovementType.INITIAL_STOCK;
    }

    private boolean isStockOutMovement(StockMovementType type) {
        return type == StockMovementType.STOCK_OUT ||
                type == StockMovementType.ADJUSTMENT_OUT ||
                type == StockMovementType.ORDER_RESERVED;
    }

    public Long getTodayMovementsCount() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(23, 59, 59);

        return stockMovementRepository.countByMovementDateBetweenAndStatus(
                startOfDay, endOfDay, EntityStatus.ACTIVE);
    }

    public Map<String, Object> getSystemPerformanceMetrics() {
        logger.info("Fetching stock system performance metrics");

        Map<String, Object> metrics = new HashMap<>();

        // Son 24 saat içindeki hareketler
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
        Long recentMovements = stockMovementRepository.countByMovementDateAfterAndStatus(
                yesterday, EntityStatus.ACTIVE);

        // En aktif ürünler (son 30 gün)
        LocalDateTime lastMonth = LocalDateTime.now().minusDays(30);
        List<StockMovement> monthlyMovements = stockMovementRepository
                .findByMovementDateAfterAndStatus(lastMonth, EntityStatus.ACTIVE);

        metrics.put("recentMovementsCount", recentMovements);
        metrics.put("monthlyMovementsCount", monthlyMovements.size());
        metrics.put("averageDailyMovements", monthlyMovements.size() / 30.0);
        metrics.put("lastCalculated", LocalDateTime.now());

        return metrics;
    }

    @Transactional
    public void trackExcelStockUpdate(Product product, Integer oldStock, Integer newStock,
                                      String operation, AppUser performedBy, String fileName) {
        logger.info("Tracking Excel stock update for product: " + product.getCode() +
                ", operation: " + operation + ", old: " + oldStock + ", new: " + newStock);

        if (oldStock == null) oldStock = 0;
        if (newStock == null) newStock = 0;

        if (oldStock.equals(newStock)) {
            return; // Değişiklik yok
        }

        StockMovementType movementType = "import".equalsIgnoreCase(operation) ?
                StockMovementType.EXCEL_IMPORT : StockMovementType.EXCEL_UPDATE;

        Integer quantity = Math.abs(newStock - oldStock);
        String notes = String.format("Excel %s işlemi - Dosya: %s (Önceki: %d, Yeni: %d)",
                operation, fileName, oldStock, newStock);

        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setMovementType(movementType);
        movement.setQuantity(quantity);
        movement.setPreviousStock(oldStock);
        movement.setNewStock(newStock);
        movement.setMovementDate(LocalDateTime.now());
        movement.setPerformedBy(performedBy != null ? performedBy.getId() : null);
        movement.setReferenceType("EXCEL");
        movement.setNotes(notes);
        movement.setStatus(EntityStatus.ACTIVE);

        stockMovementRepository.save(movement);
        logger.info("Excel stock movement tracked successfully");
    }
}