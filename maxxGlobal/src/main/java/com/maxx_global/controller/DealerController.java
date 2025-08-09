package com.maxx_global.controller;

import com.maxx_global.dto.BaseResponse;
import com.maxx_global.dto.dealer.DealerRequest;
import com.maxx_global.dto.dealer.DealerResponse;
import com.maxx_global.dto.dealer.DealerSummary;
import com.maxx_global.service.DealerService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api/dealers")
public class DealerController {

    private static final Logger logger = Logger.getLogger(DealerController.class.getName());
    private final DealerService dealerService;

    public DealerController(DealerService dealerService) {
        this.dealerService = dealerService;
    }

    // Tüm bayileri getir (sayfalama ve sıralama ile)
    @GetMapping
    public ResponseEntity<BaseResponse<Page<DealerResponse>>> getAllDealers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection) {

        try {
            logger.info("GET /api/dealers - page: " + page + ", size: " + size +
                    ", sortBy: " + sortBy + ", direction: " + sortDirection);

            Page<DealerResponse> dealers = dealerService.getAllDealers(page, size, sortBy, sortDirection);
            return ResponseEntity.ok(BaseResponse.success(dealers));

        } catch (Exception e) {
            logger.severe("Error fetching dealers: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bayiler getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    // Aktif bayileri getir
    @GetMapping("/active")
    public ResponseEntity<BaseResponse<List<DealerResponse>>> getActiveDealers() {
        try {
            logger.info("GET /api/dealers/active");
            List<DealerResponse> dealers = dealerService.getActiveDealers();
            return ResponseEntity.ok(BaseResponse.success(dealers));

        } catch (Exception e) {
            logger.severe("Error fetching active dealers: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Aktif bayiler getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    // Bayi özetlerini getir (dropdown için)
    @GetMapping("/summaries")
    public ResponseEntity<BaseResponse<List<DealerSummary>>> getDealerSummaries() {
        try {
            logger.info("GET /api/dealers/summaries");
            List<DealerSummary> summaries = dealerService.getDealerSummaries();
            return ResponseEntity.ok(BaseResponse.success(summaries));

        } catch (Exception e) {
            logger.severe("Error fetching dealer summaries: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bayi özetleri getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    // ID ile bayi getir
    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<DealerResponse>> getDealerById(@PathVariable @Min(1) Long id) {
        try {
            logger.info("GET /api/dealers/" + id);
            DealerResponse dealer = dealerService.getDealerById(id);
            return ResponseEntity.ok(BaseResponse.success(dealer));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error fetching dealer by id: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bayi getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    // İsme göre bayi arama (eski method - backward compatibility için)
    @GetMapping("/search-by-name")
    public ResponseEntity<BaseResponse<Page<DealerResponse>>> searchDealersByName(
            @RequestParam String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        try {
            logger.info("GET /api/dealers/search-by-name - name: " + name);
            Page<DealerResponse> dealers = dealerService.searchDealersByName(name, page, size);
            return ResponseEntity.ok(BaseResponse.success(dealers));

        } catch (Exception e) {
            logger.severe("Error searching dealers by name: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bayi arama sırasında bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    // Genel arama (name, email, phone, mobile alanlarında)
    @GetMapping("/search")
    public ResponseEntity<BaseResponse<Page<DealerResponse>>> searchDealers(
            @RequestParam String q, // query parameter
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection,
            @RequestParam(defaultValue = "false") boolean activeOnly) {

        try {
            logger.info("GET /api/dealers/search - q: " + q + ", activeOnly: " + activeOnly);

            Page<DealerResponse> dealers;
            if (activeOnly) {
                dealers = dealerService.searchActiveDealers(q, page, size, sortBy, sortDirection);
            } else {
                dealers = dealerService.searchDealers(q, page, size, sortBy, sortDirection);
            }

            return ResponseEntity.ok(BaseResponse.success(dealers));

        } catch (Exception e) {
            logger.severe("Error searching dealers: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bayi arama sırasında bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    // Email ile bayi getir
    @GetMapping("/by-email")
    public ResponseEntity<BaseResponse<DealerResponse>> getDealerByEmail(@RequestParam String email) {
        try {
            logger.info("GET /api/dealers/by-email - email: " + email);
            DealerResponse dealer = dealerService.getDealerByEmail(email);
            return ResponseEntity.ok(BaseResponse.success(dealer));

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error fetching dealer by email: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Email ile bayi arama sırasında bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    // Yeni bayi oluştur
    @PostMapping
    public ResponseEntity<BaseResponse<DealerResponse>> createDealer(@Valid @RequestBody DealerRequest request) {
        try {
            logger.info("POST /api/dealers - Creating dealer: " + request.name());

            DealerResponse dealer = dealerService.createDealer(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.success(dealer));

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.CONFLICT.value()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (Exception e) {
            logger.severe("Error creating dealer: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bayi oluşturulurken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    // Bayi güncelle
    @PutMapping("/{id}")
    public ResponseEntity<BaseResponse<DealerResponse>> updateDealer(
            @PathVariable @Min(1) Long id,
            @Valid @RequestBody DealerRequest request) {

        try {
            logger.info("PUT /api/dealers/" + id + " - Updating dealer");


            DealerResponse dealer = dealerService.updateDealer(id, request);
            return ResponseEntity.ok(BaseResponse.success(dealer));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.CONFLICT.value()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (Exception e) {
            logger.severe("Error updating dealer: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bayi güncellenirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    // Bayi sil (soft delete)
    @DeleteMapping("/{id}")
    public ResponseEntity<BaseResponse<Void>> deleteDealer(@PathVariable @Min(1) Long id) {
        try {
            logger.info("DELETE /api/dealers/" + id);
            dealerService.deleteDealer(id);
            return ResponseEntity.ok(BaseResponse.success(null));

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error deleting dealer: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bayi silinirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    // Bayi geri yükle
    @PostMapping("/{id}/restore")
    public ResponseEntity<BaseResponse<DealerResponse>> restoreDealer(@PathVariable @Min(1) Long id) {
        try {
            logger.info("POST /api/dealers/" + id + "/restore");
            DealerResponse dealer = dealerService.restoreDealer(id);
            return ResponseEntity.ok(BaseResponse.success(dealer));

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error restoring dealer: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Bayi geri yüklenirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }
}
