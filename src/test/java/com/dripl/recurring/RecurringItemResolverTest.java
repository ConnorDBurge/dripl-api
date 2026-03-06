package com.dripl.recurring;

import com.dripl.account.enums.CurrencyCode;
import com.dripl.common.exception.BadRequestException;
import com.dripl.common.exception.ResourceNotFoundException;
import com.dripl.recurring.dto.CreateRecurringItemInput;
import com.dripl.recurring.dto.RecurringItemMonthViewResponse;
import com.dripl.recurring.dto.RecurringItemOverrideResponse;
import com.dripl.recurring.dto.RecurringItemResponse;
import com.dripl.recurring.dto.SetOccurrenceOverrideInput;
import com.dripl.recurring.dto.UpdateOccurrenceOverrideInput;
import com.dripl.recurring.dto.UpdateRecurringItemInput;
import com.dripl.recurring.entity.RecurringItem;
import com.dripl.recurring.entity.RecurringItemOverride;
import com.dripl.recurring.enums.FrequencyGranularity;
import com.dripl.recurring.enums.RecurringItemStatus;
import com.dripl.recurring.mapper.RecurringItemMapper;
import com.dripl.recurring.resolver.RecurringItemResolver;
import com.dripl.recurring.service.RecurringItemService;
import com.dripl.recurring.service.RecurringItemViewService;
import graphql.schema.DataFetchingEnvironment;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecurringItemResolverTest {

    @Mock
    private RecurringItemService recurringItemService;

    @Mock
    private RecurringItemViewService recurringItemViewService;

    @Spy
    private RecurringItemMapper recurringItemMapper = Mappers.getMapper(RecurringItemMapper.class);

    @InjectMocks
    private RecurringItemResolver recurringItemResolver;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID recurringItemId = UUID.randomUUID();

    @BeforeEach
    void setUpSecurityContext() {
        Claims claims = mock(Claims.class);
        when(claims.get("workspace_id", String.class)).thenReturn(workspaceId.toString());
        var auth = new UsernamePasswordAuthenticationToken(claims, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

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
    void recurringItems_returnsList() {
        when(recurringItemService.listAllByWorkspaceId(workspaceId))
                .thenReturn(List.of(buildRecurringItem()));

        List<RecurringItemResponse> result = recurringItemResolver.recurringItems();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDescription()).isEqualTo("Netflix");
    }

    @Test
    void recurringItems_emptyList() {
        when(recurringItemService.listAllByWorkspaceId(workspaceId)).thenReturn(List.of());

        List<RecurringItemResponse> result = recurringItemResolver.recurringItems();

        assertThat(result).isEmpty();
    }

    @Test
    void recurringItem_returnsById() {
        when(recurringItemService.getRecurringItem(recurringItemId, workspaceId))
                .thenReturn(buildRecurringItem());

        RecurringItemResponse result = recurringItemResolver.recurringItem(recurringItemId);

        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("-15.99"));
    }

    @Test
    void createRecurringItem_delegatesToService() {
        CreateRecurringItemInput input = CreateRecurringItemInput.builder()
                .accountId(UUID.randomUUID())
                .merchantName("Netflix")
                .description("Netflix")
                .amount(new BigDecimal("-15.99"))
                .frequencyGranularity(FrequencyGranularity.MONTH)
                .frequencyQuantity(1)
                .anchorDates(List.of(LocalDateTime.of(2025, 1, 15, 0, 0)))
                .startDate(LocalDateTime.of(2025, 1, 1, 0, 0))
                .build();
        when(recurringItemService.createRecurringItem(eq(workspaceId), any(CreateRecurringItemInput.class)))
                .thenReturn(buildRecurringItem());

        RecurringItemResponse result = recurringItemResolver.createRecurringItem(input);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(recurringItemId);
        assertThat(result.getDescription()).isEqualTo("Netflix");
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("-15.99"));
        assertThat(result.getFrequencyGranularity()).isEqualTo(FrequencyGranularity.MONTH);
        assertThat(result.getFrequencyQuantity()).isEqualTo(1);
        assertThat(result.getStatus()).isEqualTo(RecurringItemStatus.ACTIVE);
        assertThat(result.getCurrencyCode()).isEqualTo(CurrencyCode.USD);
    }

    @Test
    void updateRecurringItem_delegatesToService() {
        UpdateRecurringItemInput input = UpdateRecurringItemInput.builder()
                .amount(new BigDecimal("-99.99")).build();
        DataFetchingEnvironment env = mock(DataFetchingEnvironment.class);
        when(env.getArgument("input")).thenReturn(Map.of("amount", -99.99));
        when(recurringItemService.updateRecurringItem(eq(recurringItemId), eq(workspaceId), any(UpdateRecurringItemInput.class)))
                .thenReturn(buildRecurringItem());

        RecurringItemResponse result = recurringItemResolver.updateRecurringItem(recurringItemId, input, env);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(recurringItemId);
        assertThat(result.getDescription()).isEqualTo("Netflix");
    }

    @Test
    void updateRecurringItem_appliesSpecifiedFlags() {
        UpdateRecurringItemInput input = UpdateRecurringItemInput.builder().build();
        DataFetchingEnvironment env = mock(DataFetchingEnvironment.class);
        Map<String, Object> rawInput = new java.util.HashMap<>();
        rawInput.put("categoryId", null);
        rawInput.put("notes", null);
        when(env.getArgument("input")).thenReturn(rawInput);
        when(recurringItemService.updateRecurringItem(eq(recurringItemId), eq(workspaceId), any(UpdateRecurringItemInput.class)))
                .thenReturn(buildRecurringItem());

        recurringItemResolver.updateRecurringItem(recurringItemId, input, env);

        assertThat(input.isCategoryIdSpecified()).isTrue();
        assertThat(input.isNotesSpecified()).isTrue();
    }

    @Test
    void deleteRecurringItem_delegatesToService() {
        boolean result = recurringItemResolver.deleteRecurringItem(recurringItemId);

        assertThat(result).isTrue();
        verify(recurringItemService).deleteRecurringItem(recurringItemId, workspaceId);
    }

    @Test
    void recurringItemMonthView_defaultMonth() {
        RecurringItemMonthViewResponse view = RecurringItemMonthViewResponse.builder()
                .monthStart(LocalDate.now().withDayOfMonth(1))
                .monthEnd(YearMonth.now().atEndOfMonth())
                .items(List.of())
                .expectedExpenses(BigDecimal.ZERO)
                .expectedIncome(BigDecimal.ZERO)
                .itemCount(0)
                .occurrenceCount(0)
                .build();
        when(recurringItemViewService.getMonthView(workspaceId, null, null)).thenReturn(view);

        RecurringItemMonthViewResponse result = recurringItemResolver.recurringItemMonthView(null, null);

        assertThat(result).isNotNull();
        assertThat(result.getMonthStart()).isEqualTo(LocalDate.now().withDayOfMonth(1));
        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void recurringItemMonthView_withMonth() {
        YearMonth target = YearMonth.of(2026, 5);
        RecurringItemMonthViewResponse view = RecurringItemMonthViewResponse.builder()
                .monthStart(LocalDate.of(2026, 5, 1))
                .monthEnd(LocalDate.of(2026, 5, 31))
                .items(List.of())
                .expectedExpenses(BigDecimal.ZERO)
                .expectedIncome(BigDecimal.ZERO)
                .itemCount(0)
                .occurrenceCount(0)
                .build();
        when(recurringItemViewService.getMonthView(workspaceId, target, null)).thenReturn(view);

        RecurringItemMonthViewResponse result = recurringItemResolver.recurringItemMonthView("2026-05", null);

        assertThat(result.getMonthStart()).isEqualTo(LocalDate.of(2026, 5, 1));
    }

    @Test
    void recurringItemMonthView_bothParams_delegatesToService() {
        when(recurringItemViewService.getMonthView(workspaceId, YearMonth.of(2026, 3), 1))
                .thenThrow(new BadRequestException("Cannot specify both 'month' and 'periodOffset'"));

        assertThatThrownBy(() ->
                recurringItemResolver.recurringItemMonthView("2026-03", 1))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("both");
    }

    @Test
    void createRecurringItemOverride_delegatesToService() {
        UUID overrideId = UUID.randomUUID();
        SetOccurrenceOverrideInput input = SetOccurrenceOverrideInput.builder()
                .occurrenceDate(LocalDate.of(2026, 3, 15))
                .amount(new BigDecimal("250.00"))
                .notes("Higher this month")
                .build();
        RecurringItemOverride saved = RecurringItemOverride.builder()
                .id(overrideId)
                .workspaceId(workspaceId)
                .recurringItemId(recurringItemId)
                .occurrenceDate(LocalDate.of(2026, 3, 15))
                .amount(new BigDecimal("250.00"))
                .notes("Higher this month")
                .build();
        when(recurringItemService.createOverride(recurringItemId, workspaceId, input)).thenReturn(saved);

        RecurringItemOverrideResponse result = recurringItemResolver.createRecurringItemOverride(recurringItemId, input);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(overrideId);
        assertThat(result.getRecurringItemId()).isEqualTo(recurringItemId);
        assertThat(result.getOccurrenceDate()).isEqualTo(LocalDate.of(2026, 3, 15));
        assertThat(result.getAmount()).isEqualByComparingTo("250.00");
        assertThat(result.getNotes()).isEqualTo("Higher this month");
    }

    @Test
    void createRecurringItemOverride_invalidDate_throws() {
        SetOccurrenceOverrideInput input = SetOccurrenceOverrideInput.builder()
                .occurrenceDate(LocalDate.of(2026, 3, 20))
                .amount(new BigDecimal("100.00"))
                .build();
        when(recurringItemService.createOverride(recurringItemId, workspaceId, input))
                .thenThrow(new BadRequestException("Date 2026-03-20 is not a valid occurrence of this recurring item"));

        assertThatThrownBy(() -> recurringItemResolver.createRecurringItemOverride(recurringItemId, input))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not a valid occurrence");
    }

    @Test
    void updateRecurringItemOverride_delegatesToService() {
        UUID overrideId = UUID.randomUUID();
        UpdateOccurrenceOverrideInput input = UpdateOccurrenceOverrideInput.builder()
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
        when(recurringItemService.updateOverride(overrideId, workspaceId, input)).thenReturn(saved);

        RecurringItemOverrideResponse result = recurringItemResolver.updateRecurringItemOverride(
                recurringItemId, overrideId, input);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(overrideId);
        assertThat(result.getAmount()).isEqualByComparingTo("300.00");
        assertThat(result.getNotes()).isEqualTo("Updated amount");
    }

    @Test
    void updateRecurringItemOverride_notFound_throws() {
        UUID overrideId = UUID.randomUUID();
        UpdateOccurrenceOverrideInput input = UpdateOccurrenceOverrideInput.builder()
                .amount(new BigDecimal("100.00"))
                .build();
        when(recurringItemService.updateOverride(overrideId, workspaceId, input))
                .thenThrow(new ResourceNotFoundException("Override not found"));

        assertThatThrownBy(() -> recurringItemResolver.updateRecurringItemOverride(
                recurringItemId, overrideId, input))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void deleteRecurringItemOverride_delegatesToService() {
        UUID overrideId = UUID.randomUUID();

        boolean result = recurringItemResolver.deleteRecurringItemOverride(recurringItemId, overrideId);

        assertThat(result).isTrue();
        verify(recurringItemService).deleteOverride(overrideId, workspaceId);
    }

    @Test
    void deleteRecurringItemOverride_notFound_throws() {
        UUID overrideId = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("Override not found"))
                .when(recurringItemService).deleteOverride(overrideId, workspaceId);

        assertThatThrownBy(() -> recurringItemResolver.deleteRecurringItemOverride(recurringItemId, overrideId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found");
    }
}
