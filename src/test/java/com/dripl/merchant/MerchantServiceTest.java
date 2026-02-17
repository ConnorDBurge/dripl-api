package com.dripl.merchant;

import com.dripl.common.exception.ConflictException;
import com.dripl.common.exception.ResourceNotFoundException;
import com.dripl.merchant.dto.CreateMerchantDto;
import com.dripl.merchant.dto.UpdateMerchantDto;
import com.dripl.merchant.entity.Merchant;
import com.dripl.merchant.enums.MerchantStatus;
import com.dripl.merchant.mapper.MerchantMapper;
import com.dripl.merchant.repository.MerchantRepository;
import com.dripl.merchant.service.MerchantService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantServiceTest {

    @Mock
    private MerchantRepository merchantRepository;

    @Spy
    private MerchantMapper merchantMapper = Mappers.getMapper(MerchantMapper.class);

    @InjectMocks
    private MerchantService merchantService;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID merchantId = UUID.randomUUID();

    private Merchant buildMerchant(String name) {
        return Merchant.builder()
                .id(merchantId)
                .workspaceId(workspaceId)
                .name(name)
                .status(MerchantStatus.ACTIVE)
                .build();
    }

    // --- listAllByWorkspaceId ---

    @Test
    void listAllByWorkspaceId_returnsMerchants() {
        Merchant merchant = buildMerchant("Amazon");
        when(merchantRepository.findAllByWorkspaceId(workspaceId)).thenReturn(List.of(merchant));

        List<Merchant> result = merchantService.listAllByWorkspaceId(workspaceId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Amazon");
    }

    @Test
    void listAllByWorkspaceId_emptyList() {
        when(merchantRepository.findAllByWorkspaceId(workspaceId)).thenReturn(List.of());

        List<Merchant> result = merchantService.listAllByWorkspaceId(workspaceId);

        assertThat(result).isEmpty();
    }

    // --- getMerchant ---

    @Test
    void getMerchant_found() {
        Merchant merchant = buildMerchant("Amazon");
        when(merchantRepository.findByIdAndWorkspaceId(merchantId, workspaceId)).thenReturn(Optional.of(merchant));

        Merchant result = merchantService.getMerchant(merchantId, workspaceId);

        assertThat(result.getName()).isEqualTo("Amazon");
    }

    @Test
    void getMerchant_notFound_throws() {
        when(merchantRepository.findByIdAndWorkspaceId(merchantId, workspaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> merchantService.getMerchant(merchantId, workspaceId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Merchant not found");
    }

    // --- createMerchant ---

    @Test
    void createMerchant_success() {
        CreateMerchantDto dto = CreateMerchantDto.builder()
                .name("Walmart")
                .build();

        when(merchantRepository.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, "Walmart")).thenReturn(false);
        when(merchantRepository.save(any(Merchant.class))).thenAnswer(inv -> inv.getArgument(0));

        Merchant result = merchantService.createMerchant(workspaceId, dto);

        assertThat(result.getName()).isEqualTo("Walmart");
        assertThat(result.getStatus()).isEqualTo(MerchantStatus.ACTIVE);
        assertThat(result.getWorkspaceId()).isEqualTo(workspaceId);
    }

    @Test
    void createMerchant_duplicateName_throws() {
        CreateMerchantDto dto = CreateMerchantDto.builder()
                .name("Amazon")
                .build();

        when(merchantRepository.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, "Amazon")).thenReturn(true);

        assertThatThrownBy(() -> merchantService.createMerchant(workspaceId, dto))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");
        verify(merchantRepository, never()).save(any());
    }

    // --- updateMerchant ---

    @Test
    void updateMerchant_name() {
        Merchant merchant = buildMerchant("Amazon");
        when(merchantRepository.findByIdAndWorkspaceId(merchantId, workspaceId)).thenReturn(Optional.of(merchant));
        when(merchantRepository.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, "Amazon.com")).thenReturn(false);
        when(merchantRepository.save(any(Merchant.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateMerchantDto dto = UpdateMerchantDto.builder().name("Amazon.com").build();
        Merchant result = merchantService.updateMerchant(merchantId, workspaceId, dto);

        assertThat(result.getName()).isEqualTo("Amazon.com");
    }

    @Test
    void updateMerchant_duplicateName_throws() {
        Merchant merchant = buildMerchant("Amazon");
        when(merchantRepository.findByIdAndWorkspaceId(merchantId, workspaceId)).thenReturn(Optional.of(merchant));
        when(merchantRepository.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, "Target")).thenReturn(true);

        UpdateMerchantDto dto = UpdateMerchantDto.builder().name("Target").build();

        assertThatThrownBy(() -> merchantService.updateMerchant(merchantId, workspaceId, dto))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateMerchant_sameName_allowed() {
        Merchant merchant = buildMerchant("Amazon");
        when(merchantRepository.findByIdAndWorkspaceId(merchantId, workspaceId)).thenReturn(Optional.of(merchant));
        when(merchantRepository.save(any(Merchant.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateMerchantDto dto = UpdateMerchantDto.builder().name("Amazon").build();
        Merchant result = merchantService.updateMerchant(merchantId, workspaceId, dto);

        assertThat(result.getName()).isEqualTo("Amazon");
    }

    @Test
    void updateMerchant_status() {
        Merchant merchant = buildMerchant("Amazon");
        when(merchantRepository.findByIdAndWorkspaceId(merchantId, workspaceId)).thenReturn(Optional.of(merchant));
        when(merchantRepository.save(any(Merchant.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateMerchantDto dto = UpdateMerchantDto.builder().status(MerchantStatus.ARCHIVED).build();
        Merchant result = merchantService.updateMerchant(merchantId, workspaceId, dto);

        assertThat(result.getStatus()).isEqualTo(MerchantStatus.ARCHIVED);
        assertThat(result.getName()).isEqualTo("Amazon");
    }

    @Test
    void updateMerchant_notFound_throws() {
        when(merchantRepository.findByIdAndWorkspaceId(merchantId, workspaceId)).thenReturn(Optional.empty());

        UpdateMerchantDto dto = UpdateMerchantDto.builder().name("X").build();

        assertThatThrownBy(() -> merchantService.updateMerchant(merchantId, workspaceId, dto))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- deleteMerchant ---

    @Test
    void deleteMerchant_success() {
        Merchant merchant = buildMerchant("Amazon");
        when(merchantRepository.findByIdAndWorkspaceId(merchantId, workspaceId)).thenReturn(Optional.of(merchant));

        merchantService.deleteMerchant(merchantId, workspaceId);

        verify(merchantRepository).delete(merchant);
    }

    @Test
    void deleteMerchant_notFound_throws() {
        when(merchantRepository.findByIdAndWorkspaceId(merchantId, workspaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> merchantService.deleteMerchant(merchantId, workspaceId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(merchantRepository, never()).delete(any());
    }
}
