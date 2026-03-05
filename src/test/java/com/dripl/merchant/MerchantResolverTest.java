package com.dripl.merchant;

import com.dripl.common.enums.Status;
import com.dripl.merchant.dto.CreateMerchantInput;
import com.dripl.merchant.dto.MerchantResponse;
import com.dripl.merchant.dto.UpdateMerchantInput;
import com.dripl.merchant.entity.Merchant;
import com.dripl.merchant.mapper.MerchantMapper;
import com.dripl.merchant.resolver.MerchantResolver;
import com.dripl.merchant.service.MerchantService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantResolverTest {

    @Mock
    private MerchantService merchantService;

    @Spy
    private MerchantMapper merchantMapper = Mappers.getMapper(MerchantMapper.class);

    @InjectMocks
    private MerchantResolver merchantResolver;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID merchantId = UUID.randomUUID();

    @BeforeEach
    void setUpSecurityContext() {
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(claims.get("workspace_id", String.class)).thenReturn(workspaceId.toString());
        var auth = new UsernamePasswordAuthenticationToken(claims, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Merchant buildMerchant(String name) {
        return Merchant.builder()
                .id(merchantId)
                .workspaceId(workspaceId)
                .name(name)
                .status(Status.ACTIVE)
                .build();
    }

    @Test
    void merchants_returnsList() {
        when(merchantService.listAllByWorkspaceId(workspaceId))
                .thenReturn(List.of(buildMerchant("Amazon")));

        List<MerchantResponse> result = merchantResolver.merchants();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Amazon");
    }

    @Test
    void merchant_returnsById() {
        when(merchantService.getMerchant(merchantId, workspaceId))
                .thenReturn(buildMerchant("Amazon"));

        MerchantResponse result = merchantResolver.merchant(merchantId);

        assertThat(result.getName()).isEqualTo("Amazon");
    }

    @Test
    void createMerchant_delegatesToService() {
        CreateMerchantInput input = CreateMerchantInput.builder().name("Walmart").build();
        when(merchantService.createMerchant(eq(workspaceId), any(CreateMerchantInput.class)))
                .thenReturn(buildMerchant("Walmart"));

        MerchantResponse result = merchantResolver.createMerchant(input);

        assertThat(result.getName()).isEqualTo("Walmart");
    }

    @Test
    void updateMerchant_delegatesToService() {
        UpdateMerchantInput input = UpdateMerchantInput.builder().name("Updated").build();
        when(merchantService.updateMerchant(eq(merchantId), eq(workspaceId), any(UpdateMerchantInput.class)))
                .thenReturn(buildMerchant("Updated"));

        MerchantResponse result = merchantResolver.updateMerchant(merchantId, input);

        assertThat(result.getName()).isEqualTo("Updated");
    }

    @Test
    void deleteMerchant_delegatesToService() {
        boolean result = merchantResolver.deleteMerchant(merchantId);

        assertThat(result).isTrue();
        verify(merchantService).deleteMerchant(merchantId, workspaceId);
    }
}
