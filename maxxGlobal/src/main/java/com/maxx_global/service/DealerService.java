package com.maxx_global.service;

import com.maxx_global.dto.dealer.DealerMapper;
import com.maxx_global.dto.dealer.DealerRequest;
import com.maxx_global.dto.dealer.DealerResponse;
import com.maxx_global.dto.dealer.DealerSummary;
import com.maxx_global.entity.Dealer;
import com.maxx_global.enums.EntityStatus;
import com.maxx_global.repository.DealerRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DealerService {

    private static final Logger logger = Logger.getLogger(DealerService.class.getName());

    private final DealerRepository dealerRepository;
    private final DealerMapper dealerMapper;

    public DealerService(DealerRepository dealerRepository, DealerMapper dealerMapper) {
        this.dealerRepository = dealerRepository;
        this.dealerMapper = dealerMapper;
    }

    // Tüm bayileri getir (sayfalama ve sıralama ile)
    public Page<DealerResponse> getAllDealers(int page, int size, String sortBy, String sortDirection) {
        logger.info("Fetching dealers - page: " + page + ", size: " + size +
                ", sortBy: " + sortBy + ", direction: " + sortDirection);

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Dealer> dealers = dealerRepository.findAll(pageable);
        return dealers.map(dealerMapper::toResponse);
    }

    // Aktif bayileri getir
    public List<DealerResponse> getActiveDealers() {
        logger.info("Fetching active dealers");
        List<Dealer> dealers = dealerRepository.findByStatusOrderByNameAsc("ACTIVE");
        return dealers.stream()
                .map(dealerMapper::toResponse)
                .collect(Collectors.toList());
    }

    // Bayi özeti listesi (dropdown vs. için)
    public List<DealerSummary> getDealerSummaries() {
        logger.info("Fetching dealer summaries");
        List<Dealer> dealers = dealerRepository.findByStatusOrderByNameAsc("ACTIVE");
        return dealers.stream()
                .map(dealerMapper::toSummary)
                .collect(Collectors.toList());
    }

    // ID ile bayi getir
    public DealerResponse getDealerById(Long id) {
        logger.info("Fetching dealer with id: " + id);
        Dealer dealer = dealerRepository.findById(id)
                .orElseThrow(() -> new BadCredentialsException("Dealer not found with id: " + id));
        return dealerMapper.toResponse(dealer);
    }

    // İsme göre bayi arama
    public Page<DealerResponse> searchDealersByName(String name, int page, int size) {
        logger.info("Searching dealers by name: " + name);
        Pageable pageable = PageRequest.of(page, size);
        Page<Dealer> dealers = dealerRepository.findByNameContainingIgnoreCase(name, pageable);
        return dealers.map(dealerMapper::toResponse);
    }

    // Genel arama (name, email, phone, mobile alanlarında)
    public Page<DealerResponse> searchDealers(String searchTerm, int page, int size, String sortBy, String sortDirection) {
        logger.info("Searching dealers with term: " + searchTerm);

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Dealer> dealers = dealerRepository.searchDealers(searchTerm, pageable);
        return dealers.map(dealerMapper::toResponse);
    }

    // Aktif bayiler için genel arama
    public Page<DealerResponse> searchActiveDealers(String searchTerm, int page, int size, String sortBy, String sortDirection) {
        logger.info("Searching active dealers with term: " + searchTerm);

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Dealer> dealers = dealerRepository.searchActiveOrInactiveDealers(searchTerm, EntityStatus.ACTIVE, pageable);
        return dealers.map(dealerMapper::toResponse);
    }

    // Yeni bayi oluştur
    @Transactional
    public DealerResponse createDealer(DealerRequest request) {
        logger.info("Creating new dealer: " + request.name());

        // Email benzersizlik kontrolü
        if (dealerRepository.existsByEmailIgnoreCase(request.email())) {
            throw new BadCredentialsException("Email already exists: " + request.email());
        }

        // Bayi adı benzersizlik kontrolü
        if (dealerRepository.existsByNameIgnoreCase(request.name())) {
            throw new BadCredentialsException("Dealer name already exists: " + request.name());
        }

        Dealer dealer = dealerMapper.toEntity(request);
        dealer.setStatus(EntityStatus.ACTIVE);

        Dealer savedDealer = dealerRepository.save(dealer);
        logger.info("Dealer created successfully with id: " + savedDealer.getId());

        return dealerMapper.toResponse(savedDealer);
    }

    // Bayi güncelle
    @Transactional
    public DealerResponse updateDealer(Long id, DealerRequest request) {
        logger.info("Updating dealer with id: " + id);

        Dealer existingDealer = dealerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Dealer not found with id: " + id));

        // Email benzersizlik kontrolü (mevcut bayi hariç)
        if (dealerRepository.existsByEmailIgnoreCaseAndIdNot(request.email(), id)) {
            throw new BadCredentialsException("Email already exists: " + request.email());
        }

        // Bayi adı benzersizlik kontrolü (mevcut bayi hariç)
        if (dealerRepository.existsByNameIgnoreCaseAndIdNot(request.name(), id)) {
            throw new BadCredentialsException("Dealer name already exists: " + request.name());
        }

        // Güncelleme işlemi
        existingDealer.setName(request.name());
        existingDealer.setPhone(request.fixedPhone());
        existingDealer.setMobile(request.mobilePhone());
        existingDealer.setEmail(request.email());
        existingDealer.setAddress(request.address());

        Dealer updatedDealer = dealerRepository.save(existingDealer);
        logger.info("Dealer updated successfully with id: " + updatedDealer.getId());

        return dealerMapper.toResponse(updatedDealer);
    }


    // Bayi sil (soft delete)
    @Transactional
    public void deleteDealer(Long id) {
        logger.info("Deleting dealer with id: " + id);

        Dealer dealer = dealerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Dealer not found with id: " + id));

        // Soft delete
        dealer.setStatus(EntityStatus.DELETED);
        dealerRepository.save(dealer);

        logger.info("Dealer deleted successfully with id: " + id);
    }

    // Email ile bayi bulma
    public DealerResponse getDealerByEmail(String email) {
        logger.info("Fetching dealer by email: " + email);
        Dealer dealer = dealerRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new EntityNotFoundException("Dealer not found with email: " + email));
        return dealerMapper.toResponse(dealer);
    }

    // Bayi geri yükleme
    @Transactional
    public DealerResponse restoreDealer(Long id) {
        logger.info("Restoring dealer with id: " + id);

        Dealer dealer = dealerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Dealer not found with id: " + id));

        dealer.setStatus(EntityStatus.ACTIVE);

        Dealer restoredDealer = dealerRepository.save(dealer);
        logger.info("Dealer restored successfully with id: " + id);

        return dealerMapper.toResponse(restoredDealer);
    }
}