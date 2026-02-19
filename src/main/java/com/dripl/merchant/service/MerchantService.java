package com.dripl.merchant.service;

import com.dripl.common.exception.ConflictException;
import com.dripl.common.exception.ResourceNotFoundException;
import com.dripl.merchant.dto.CreateMerchantDto;
import com.dripl.merchant.dto.UpdateMerchantDto;
import com.dripl.merchant.entity.Merchant;
import com.dripl.common.enums.Status;
import com.dripl.merchant.mapper.MerchantMapper;
import com.dripl.merchant.repository.MerchantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final MerchantMapper merchantMapper;

    @Transactional(readOnly = true)
    public List<Merchant> listAllByWorkspaceId(UUID workspaceId) {
        return merchantRepository.findAllByWorkspaceId(workspaceId);
    }

    @Transactional(readOnly = true)
    public Merchant getMerchant(UUID merchantId, UUID workspaceId) {
        return merchantRepository.findByIdAndWorkspaceId(merchantId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found"));
    }

    @Transactional
    public Merchant createMerchant(UUID workspaceId, CreateMerchantDto dto) {
        if (merchantRepository.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, dto.getName())) {
            throw new ConflictException("A merchant named '" + dto.getName() + "' already exists");
        }

        Merchant merchant = Merchant.builder()
                .workspaceId(workspaceId)
                .name(dto.getName())
                .status(Status.ACTIVE)
                .build();

        log.info("Created merchant '{}'", dto.getName());
        return merchantRepository.save(merchant);
    }

    @Transactional
    public Merchant updateMerchant(UUID merchantId, UUID workspaceId, UpdateMerchantDto dto) {
        Merchant merchant = getMerchant(merchantId, workspaceId);

        if (dto.getName() != null
                && !dto.getName().equalsIgnoreCase(merchant.getName())
                && merchantRepository.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, dto.getName())) {
            throw new ConflictException("A merchant named '" + dto.getName() + "' already exists");
        }

        merchantMapper.updateEntity(dto, merchant);
        log.info("Updating merchant '{}' ({})", merchant.getName(), merchantId);
        return merchantRepository.save(merchant);
    }

    @Transactional
    public void deleteMerchant(UUID merchantId, UUID workspaceId) {
        Merchant merchant = getMerchant(merchantId, workspaceId);
        log.info("Deleting merchant '{}' ({})", merchant.getName(), merchantId);
        merchantRepository.delete(merchant);
    }

    @Transactional
    public Merchant resolveMerchant(String merchantName, UUID workspaceId) {
        return merchantRepository.findByWorkspaceIdAndNameIgnoreCase(workspaceId, merchantName)
                .orElseGet(() -> {
                    Merchant merchant = Merchant.builder()
                            .workspaceId(workspaceId)
                            .name(merchantName)
                            .status(Status.ACTIVE)
                            .build();
                    Merchant saved = merchantRepository.save(merchant);
                    log.info("Auto-created merchant '{}'", merchantName);
                    return saved;
                });
    }
}
