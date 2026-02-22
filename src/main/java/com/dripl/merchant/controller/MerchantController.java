package com.dripl.merchant.controller;

import com.dripl.common.annotation.WorkspaceId;
import com.dripl.merchant.dto.CreateMerchantDto;
import com.dripl.merchant.dto.MerchantDto;
import com.dripl.merchant.dto.UpdateMerchantDto;
import com.dripl.merchant.entity.Merchant;
import com.dripl.merchant.mapper.MerchantMapper;
import com.dripl.merchant.service.MerchantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/merchants")
public class MerchantController {

    private final MerchantService merchantService;
    private final MerchantMapper merchantMapper;

    @PreAuthorize("hasAuthority('READ')")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<MerchantDto>> listMerchants(@WorkspaceId UUID workspaceId) {
        List<Merchant> merchants = merchantService.listAllByWorkspaceId(workspaceId);
        return ResponseEntity.ok(merchantMapper.toDtos(merchants));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MerchantDto> createMerchant(
            @WorkspaceId UUID workspaceId, @Valid @RequestBody CreateMerchantDto dto) {
        Merchant merchant = merchantService.createMerchant(workspaceId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(merchantMapper.toDto(merchant));
    }

    @PreAuthorize("hasAuthority('READ')")
    @GetMapping(value = "/{merchantId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MerchantDto> getMerchant(
            @WorkspaceId UUID workspaceId, @PathVariable UUID merchantId) {
        Merchant merchant = merchantService.getMerchant(merchantId, workspaceId);
        return ResponseEntity.ok(merchantMapper.toDto(merchant));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @PatchMapping(value = "/{merchantId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MerchantDto> updateMerchant(
            @PathVariable UUID merchantId,
            @WorkspaceId UUID workspaceId,
            @Valid @RequestBody UpdateMerchantDto dto) {
        Merchant merchant = merchantService.updateMerchant(merchantId, workspaceId, dto);
        return ResponseEntity.ok(merchantMapper.toDto(merchant));
    }

    @PreAuthorize("hasAuthority('DELETE')")
    @DeleteMapping("/{merchantId}")
    public ResponseEntity<Void> deleteMerchant(
            @WorkspaceId UUID workspaceId, @PathVariable UUID merchantId) {
        merchantService.deleteMerchant(merchantId, workspaceId);
        return ResponseEntity.noContent().build();
    }
}
