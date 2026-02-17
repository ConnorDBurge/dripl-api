package com.dripl.merchant;

import com.dripl.merchant.controller.MerchantController;
import com.dripl.merchant.dto.CreateMerchantDto;
import com.dripl.merchant.dto.UpdateMerchantDto;
import com.dripl.merchant.entity.Merchant;
import com.dripl.common.enums.Status;
import com.dripl.merchant.mapper.MerchantMapper;
import com.dripl.merchant.service.MerchantService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantControllerTest {

    @Mock
    private MerchantService merchantService;

    @Spy
    private MerchantMapper merchantMapper = Mappers.getMapper(MerchantMapper.class);

    @InjectMocks
    private MerchantController merchantController;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID merchantId = UUID.randomUUID();

    private Merchant buildMerchant(String name) {
        return Merchant.builder()
                .id(merchantId)
                .workspaceId(workspaceId)
                .name(name)
                .status(Status.ACTIVE)
                .build();
    }

    @Test
    void listMerchants_returns200() {
        when(merchantService.listAllByWorkspaceId(workspaceId)).thenReturn(List.of(buildMerchant("Amazon")));

        var response = merchantController.listMerchants(workspaceId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void listMerchants_empty_returns200() {
        when(merchantService.listAllByWorkspaceId(workspaceId)).thenReturn(List.of());

        var response = merchantController.listMerchants(workspaceId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void getMerchant_returns200() {
        when(merchantService.getMerchant(merchantId, workspaceId)).thenReturn(buildMerchant("Amazon"));

        var response = merchantController.getMerchant(workspaceId, merchantId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getName()).isEqualTo("Amazon");
    }

    @Test
    void createMerchant_returns201() {
        CreateMerchantDto dto = CreateMerchantDto.builder()
                .name("Amazon")
                .build();
        when(merchantService.createMerchant(eq(workspaceId), any(CreateMerchantDto.class)))
                .thenReturn(buildMerchant("Amazon"));

        var response = merchantController.createMerchant(workspaceId, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getName()).isEqualTo("Amazon");
    }

    @Test
    void updateMerchant_returns200() {
        UpdateMerchantDto dto = UpdateMerchantDto.builder().name("Updated").build();
        Merchant updated = buildMerchant("Updated");
        when(merchantService.updateMerchant(eq(merchantId), eq(workspaceId), any(UpdateMerchantDto.class)))
                .thenReturn(updated);

        var response = merchantController.updateMerchant(merchantId, workspaceId, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getName()).isEqualTo("Updated");
    }

    @Test
    void deleteMerchant_returns204() {
        var response = merchantController.deleteMerchant(workspaceId, merchantId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(merchantService).deleteMerchant(merchantId, workspaceId);
    }
}
