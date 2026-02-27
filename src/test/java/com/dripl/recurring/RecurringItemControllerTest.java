package com.dripl.recurring;

import com.dripl.account.enums.CurrencyCode;
import com.dripl.common.exception.BadRequestException;
import com.dripl.common.exception.ResourceNotFoundException;
import com.dripl.recurring.controller.RecurringItemController;
import com.dripl.recurring.dto.CreateRecurringItemDto;
import com.dripl.recurring.dto.RecurringItemMonthViewDto;
import com.dripl.recurring.dto.SetOccurrenceOverrideDto;
import com.dripl.recurring.dto.UpdateOccurrenceOverrideDto;
import com.dripl.recurring.dto.UpdateRecurringItemDto;
import com.dripl.recurring.entity.RecurringItem;
import com.dripl.recurring.entity.RecurringItemOverride;
import com.dripl.recurring.enums.FrequencyGranularity;
import com.dripl.recurring.enums.RecurringItemStatus;
import com.dripl.recurring.mapper.RecurringItemMapper;
import com.dripl.recurring.service.RecurringItemService;
import com.dripl.recurring.service.RecurringItemViewService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecurringItemControllerTest {

    @Mock
    private RecurringItemService recurringItemService;

    @Mock
    private RecurringItemViewService recurringItemViewService;

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
        var body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getId()).isEqualTo(recurringItemId);
        assertThat(body.getDescription()).isEqualTo("Netflix");
        assertThat(body.getAmount()).isEqualByComparingTo(new BigDecimal("-15.99"));
        assertThat(body.getFrequencyGranularity()).isEqualTo(FrequencyGranularity.MONTH);
        assertThat(body.getFrequencyQuantity()).isEqualTo(1);
        assertThat(body.getStatus()).isEqualTo(RecurringItemStatus.ACTIVE);
        assertThat(body.getCurrencyCode()).isEqualTo(CurrencyCode.USD);
    }

    @Test
    void updateRecurringItem_returns200() {
        UpdateRecurringItemDto dto = UpdateRecurringItemDto.builder().amount(new BigDecimal("-99.99")).build();
        when(recurringItemService.updateRecurringItem(eq(recurringItemId), eq(workspaceId), any(UpdateRecurringItemDto.class)))
                .thenReturn(buildRecurringItem());

        var response = recurringItemController.updateRecurringItem(workspaceId, recurringItemId, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getId()).isEqualTo(recurringItemId);
        assertThat(body.getDescription()).isEqualTo("Netflix");
        assertThat(body.getAmount()).isEqualByComparingTo(new BigDecimal("-15.99"));
        assertThat(body.getStatus()).isEqualTo(RecurringItemStatus.ACTIVE);
    }

    @Test
    void deleteRecurringItem_returns204() {
        var response = recurringItemController.deleteRecurringItem(workspaceId, recurringItemId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(recurringItemService).deleteRecurringItem(recurringItemId, workspaceId);
    }

    @Test
    void getMonthView_defaultMonth_returns200() {
        RecurringItemMonthViewDto view = RecurringItemMonthViewDto.builder()
                .monthStart(LocalDate.now().withDayOfMonth(1))
                .monthEnd(YearMonth.now().atEndOfMonth())
                .items(List.of())
                .expectedExpenses(BigDecimal.ZERO)
                .expectedIncome(BigDecimal.ZERO)
                .itemCount(0)
                .occurrenceCount(0)
                .build();
        when(recurringItemViewService.getMonthView(workspaceId, null, null)).thenReturn(view);

        var response = recurringItemController.getMonthView(workspaceId, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getMonthStart()).isEqualTo(LocalDate.now().withDayOfMonth(1));
        assertThat(body.getMonthEnd()).isEqualTo(YearMonth.now().atEndOfMonth());
        assertThat(body.getItems()).isEmpty();
        assertThat(body.getExpectedExpenses()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(body.getExpectedIncome()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(body.getItemCount()).isZero();
        assertThat(body.getOccurrenceCount()).isZero();
    }

    @Test
    void getMonthView_withMonth_returns200() {
        YearMonth target = YearMonth.of(2026, 5);
        RecurringItemMonthViewDto view = RecurringItemMonthViewDto.builder()
                .monthStart(LocalDate.of(2026, 5, 1))
                .monthEnd(LocalDate.of(2026, 5, 31))
                .items(List.of())
                .expectedExpenses(BigDecimal.ZERO)
                .expectedIncome(BigDecimal.ZERO)
                .itemCount(0)
                .occurrenceCount(0)
                .build();
        when(recurringItemViewService.getMonthView(workspaceId, target, null)).thenReturn(view);

        var response = recurringItemController.getMonthView(workspaceId, target, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getMonthStart()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(body.getMonthEnd()).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(body.getItems()).isEmpty();
    }

    @Test
    void getMonthView_withPeriodOffset_returns200() {
        RecurringItemMonthViewDto view = RecurringItemMonthViewDto.builder()
                .monthStart(YearMonth.now().minusMonths(2).atDay(1))
                .monthEnd(YearMonth.now().minusMonths(2).atEndOfMonth())
                .items(List.of())
                .expectedExpenses(BigDecimal.ZERO)
                .expectedIncome(BigDecimal.ZERO)
                .itemCount(0)
                .occurrenceCount(0)
                .build();
        when(recurringItemViewService.getMonthView(workspaceId, null, -2)).thenReturn(view);

        var response = recurringItemController.getMonthView(workspaceId, null, -2);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getMonthStart()).isEqualTo(YearMonth.now().minusMonths(2).atDay(1));
        assertThat(body.getMonthEnd()).isEqualTo(YearMonth.now().minusMonths(2).atEndOfMonth());
        assertThat(body.getExpectedExpenses()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(body.getExpectedIncome()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getMonthView_bothParams_delegatesToService() {
        when(recurringItemViewService.getMonthView(workspaceId, YearMonth.of(2026, 3), 1))
                .thenThrow(new BadRequestException("Cannot specify both 'month' and 'periodOffset'"));

        assertThatThrownBy(() ->
                recurringItemController.getMonthView(workspaceId, YearMonth.of(2026, 3), 1))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("both");
    }

    // --- Override endpoint tests ---

    @Test
    void createOverride_returns201() {
        UUID overrideId = UUID.randomUUID();
        SetOccurrenceOverrideDto dto = SetOccurrenceOverrideDto.builder()
                .occurrenceDate(LocalDate.of(2026, 3, 15))
                .amount(new BigDecimal("250.00"))
                .notes("Higher this month")
                .build();
        RecurringItemOverride saved = RecurringItemOverride.builder()
                .id((overrideId))
                .workspaceId(workspaceId)
                .recurringItemId(recurringItemId)
                .occurrenceDate(LocalDate.of(2026, 3, 15))
                .amount(new BigDecimal("250.00"))
                .notes("Higher this month")
                .build();
        when(recurringItemService.createOverride(recurringItemId, workspaceId, dto)).thenReturn(saved);

        var response = recurringItemController.createOverride(workspaceId, recurringItemId, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getId()).isEqualTo(overrideId);
        assertThat(body.getRecurringItemId()).isEqualTo(recurringItemId);
        assertThat(body.getOccurrenceDate()).isEqualTo(LocalDate.of(2026, 3, 15));
        assertThat(body.getAmount()).isEqualByComparingTo("250.00");
        assertThat(body.getNotes()).isEqualTo("Higher this month");
    }

    @Test
    void createOverride_invalidOccurrenceDate_throws() {
        SetOccurrenceOverrideDto dto = SetOccurrenceOverrideDto.builder()
                .occurrenceDate(LocalDate.of(2026, 3, 20))
                .amount(new BigDecimal("100.00"))
                .build();
        when(recurringItemService.createOverride(recurringItemId, workspaceId, dto))
                .thenThrow(new BadRequestException("Date 2026-03-20 is not a valid occurrence of this recurring item"));

        assertThatThrownBy(() -> recurringItemController.createOverride(workspaceId, recurringItemId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not a valid occurrence");
    }

    @Test
    void createOverride_duplicateOccurrence_throws() {
        SetOccurrenceOverrideDto dto = SetOccurrenceOverrideDto.builder()
                .occurrenceDate(LocalDate.of(2026, 3, 15))
                .amount(new BigDecimal("100.00"))
                .build();
        when(recurringItemService.createOverride(recurringItemId, workspaceId, dto))
                .thenThrow(new BadRequestException("Override already exists for occurrence 2026-03-15. Use PUT to update."));

        assertThatThrownBy(() -> recurringItemController.createOverride(workspaceId, recurringItemId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void updateOverride_returns200() {
        UUID overrideId = UUID.randomUUID();
        UpdateOccurrenceOverrideDto dto = UpdateOccurrenceOverrideDto.builder()
                .amount(new BigDecimal("300.00"))
                .notes("Updated amount")
                .build();
        RecurringItemOverride saved = RecurringItemOverride.builder()
                .id(overrideId)
                .workspaceId(workspaceId)
                .recurringItemId(recurringItemId)
                .occurrenceDate(LocalDate.of(2026, 3, 15))
                .amount(new BigDecimal("300.00"))
                .notes("Updated amount")
                .build();
        when(recurringItemService.updateOverride(overrideId, workspaceId, dto)).thenReturn(saved);

        var response = recurringItemController.updateOverride(workspaceId, recurringItemId, overrideId, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getId()).isEqualTo(overrideId);
        assertThat(body.getRecurringItemId()).isEqualTo(recurringItemId);
        assertThat(body.getAmount()).isEqualByComparingTo("300.00");
        assertThat(body.getNotes()).isEqualTo("Updated amount");
    }

    @Test
    void updateOverride_notFound_throws() {
        UUID overrideId = UUID.randomUUID();
        UpdateOccurrenceOverrideDto dto = UpdateOccurrenceOverrideDto.builder()
                .amount(new BigDecimal("100.00"))
                .build();
        when(recurringItemService.updateOverride(overrideId, workspaceId, dto))
                .thenThrow(new ResourceNotFoundException("Override not found"));

        assertThatThrownBy(() -> recurringItemController.updateOverride(workspaceId, recurringItemId, overrideId, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void deleteOverride_returns204() {
        UUID overrideId = UUID.randomUUID();

        var response = recurringItemController.deleteOverride(workspaceId, recurringItemId, overrideId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(recurringItemService).deleteOverride(overrideId, workspaceId);
    }

    @Test
    void deleteOverride_notFound_throws() {
        UUID overrideId = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("Override not found"))
                .when(recurringItemService).deleteOverride(overrideId, workspaceId);

        assertThatThrownBy(() -> recurringItemController.deleteOverride(workspaceId, recurringItemId, overrideId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found");
    }
}
