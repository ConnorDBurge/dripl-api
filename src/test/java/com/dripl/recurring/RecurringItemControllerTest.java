package com.dripl.recurring;

import com.dripl.account.enums.CurrencyCode;
import com.dripl.recurring.controller.RecurringItemController;
import com.dripl.recurring.dto.CreateRecurringItemDto;
import com.dripl.recurring.dto.UpdateRecurringItemDto;
import com.dripl.recurring.entity.RecurringItem;
import com.dripl.recurring.enums.FrequencyGranularity;
import com.dripl.recurring.enums.RecurringItemStatus;
import com.dripl.recurring.mapper.RecurringItemMapper;
import com.dripl.recurring.service.RecurringItemService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecurringItemControllerTest {

    @Mock
    private RecurringItemService recurringItemService;

    @Spy
    private RecurringItemMapper recurringItemMapper = Mappers.getMapper(RecurringItemMapper.class);

    @InjectMocks
    private RecurringItemController recurringItemController;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID recurringItemId = UUID.randomUUID();

    private RecurringItem buildRecurringItem() {
        return RecurringItem.builder()
                .id(recurringItemId)
                .workspaceId(workspaceId)
                .accountId(UUID.randomUUID())
                .merchantId(UUID.randomUUID())
                .categoryId(UUID.randomUUID())
                .description("Netflix")
                .amount(new BigDecimal("-15.99"))
                .currencyCode(CurrencyCode.USD)
                .frequencyGranularity(FrequencyGranularity.MONTH)
                .frequencyQuantity(1)
                .anchorDates(new ArrayList<>(List.of(LocalDateTime.of(2025, 1, 15, 0, 0))))
                .startDate(LocalDateTime.of(2025, 1, 1, 0, 0))
                .status(RecurringItemStatus.ACTIVE)
                .tagIds(new HashSet<>())
                .build();
    }

    @Test
    void listRecurringItems_returns200() {
        when(recurringItemService.listAllByWorkspaceId(workspaceId)).thenReturn(List.of(buildRecurringItem()));

        var response = recurringItemController.listRecurringItems(workspaceId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void listRecurringItems_empty_returns200() {
        when(recurringItemService.listAllByWorkspaceId(workspaceId)).thenReturn(List.of());

        var response = recurringItemController.listRecurringItems(workspaceId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void getRecurringItem_returns200() {
        when(recurringItemService.getRecurringItem(recurringItemId, workspaceId)).thenReturn(buildRecurringItem());

        var response = recurringItemController.getRecurringItem(workspaceId, recurringItemId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getAmount()).isEqualByComparingTo(new BigDecimal("-15.99"));
    }

    @Test
    void createRecurringItem_returns201() {
        CreateRecurringItemDto dto = CreateRecurringItemDto.builder()
                .accountId(UUID.randomUUID())
                .merchantName("Netflix")
                .description("Netflix")
                .amount(new BigDecimal("-15.99"))
                .frequencyGranularity(FrequencyGranularity.MONTH)
                .frequencyQuantity(1)
                .anchorDates(List.of(LocalDateTime.of(2025, 1, 15, 0, 0)))
                .startDate(LocalDateTime.of(2025, 1, 1, 0, 0))
                .build();
        when(recurringItemService.createRecurringItem(eq(workspaceId), any(CreateRecurringItemDto.class)))
                .thenReturn(buildRecurringItem());

        var response = recurringItemController.createRecurringItem(workspaceId, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void updateRecurringItem_returns200() {
        UpdateRecurringItemDto dto = UpdateRecurringItemDto.builder().amount(new BigDecimal("-99.99")).build();
        when(recurringItemService.updateRecurringItem(eq(recurringItemId), eq(workspaceId), any(UpdateRecurringItemDto.class)))
                .thenReturn(buildRecurringItem());

        var response = recurringItemController.updateRecurringItem(workspaceId, recurringItemId, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void deleteRecurringItem_returns204() {
        var response = recurringItemController.deleteRecurringItem(workspaceId, recurringItemId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(recurringItemService).deleteRecurringItem(recurringItemId, workspaceId);
    }
}
