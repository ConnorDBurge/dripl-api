package com.dripl.recurring.resolver;

import com.dripl.common.graphql.GraphQLContext;
import com.dripl.recurring.dto.CreateRecurringItemInput;
import com.dripl.recurring.dto.RecurringItemMonthViewResponse;
import com.dripl.recurring.dto.RecurringItemOverrideResponse;
import com.dripl.recurring.dto.RecurringItemResponse;
import com.dripl.recurring.dto.SetOccurrenceOverrideInput;
import com.dripl.recurring.dto.UpdateOccurrenceOverrideInput;
import com.dripl.recurring.dto.UpdateRecurringItemInput;
import com.dripl.recurring.entity.RecurringItemOverride;
import com.dripl.recurring.mapper.RecurringItemMapper;
import com.dripl.recurring.service.RecurringItemService;
import com.dripl.recurring.service.RecurringItemViewService;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class RecurringItemResolver {

    private final RecurringItemService recurringItemService;
    private final RecurringItemViewService recurringItemViewService;
    private final RecurringItemMapper recurringItemMapper;

    @PreAuthorize("hasAuthority('READ')")
    @QueryMapping
    public List<RecurringItemResponse> recurringItems() {
        UUID workspaceId = GraphQLContext.workspaceId();
        return recurringItemMapper.toDtos(recurringItemService.listAllByWorkspaceId(workspaceId));
    }

    @PreAuthorize("hasAuthority('READ')")
    @QueryMapping
    public RecurringItemResponse recurringItem(@Argument UUID recurringItemId) {
        UUID workspaceId = GraphQLContext.workspaceId();
        return recurringItemMapper.toDto(recurringItemService.getRecurringItem(recurringItemId, workspaceId));
    }

    @PreAuthorize("hasAuthority('READ')")
    @QueryMapping
    public RecurringItemMonthViewResponse recurringItemMonthView(
            @Argument String month, @Argument Integer periodOffset) {
        UUID workspaceId = GraphQLContext.workspaceId();
        YearMonth yearMonth = month != null ? YearMonth.parse(month) : null;
        return recurringItemViewService.getMonthView(workspaceId, yearMonth, periodOffset);
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @MutationMapping
    public RecurringItemResponse createRecurringItem(@Argument @Valid CreateRecurringItemInput input) {
        UUID workspaceId = GraphQLContext.workspaceId();
        return recurringItemMapper.toDto(recurringItemService.createRecurringItem(workspaceId, input));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @MutationMapping
    @SuppressWarnings("unchecked")
    public RecurringItemResponse updateRecurringItem(
            @Argument UUID recurringItemId, @Argument @Valid UpdateRecurringItemInput input,
            DataFetchingEnvironment env) {
        UUID workspaceId = GraphQLContext.workspaceId();

        Map<String, Object> rawInput = env.getArgument("input");
        if (rawInput != null) {
            applySpecifiedFlags(input, rawInput);
        }

        return recurringItemMapper.toDto(
                recurringItemService.updateRecurringItem(recurringItemId, workspaceId, input));
    }

    @PreAuthorize("hasAuthority('DELETE')")
    @MutationMapping
    public boolean deleteRecurringItem(@Argument UUID recurringItemId) {
        UUID workspaceId = GraphQLContext.workspaceId();
        recurringItemService.deleteRecurringItem(recurringItemId, workspaceId);
        return true;
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @MutationMapping
    public RecurringItemOverrideResponse createRecurringItemOverride(
            @Argument UUID recurringItemId, @Argument @Valid SetOccurrenceOverrideInput input) {
        UUID workspaceId = GraphQLContext.workspaceId();
        RecurringItemOverride override = recurringItemService.createOverride(recurringItemId, workspaceId, input);
        return toOverrideResponse(override);
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @MutationMapping
    public RecurringItemOverrideResponse updateRecurringItemOverride(
            @Argument UUID recurringItemId, @Argument UUID overrideId,
            @Argument @Valid UpdateOccurrenceOverrideInput input) {
        UUID workspaceId = GraphQLContext.workspaceId();
        RecurringItemOverride override = recurringItemService.updateOverride(overrideId, workspaceId, input);
        return toOverrideResponse(override);
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @MutationMapping
    public boolean deleteRecurringItemOverride(
            @Argument UUID recurringItemId, @Argument UUID overrideId) {
        UUID workspaceId = GraphQLContext.workspaceId();
        recurringItemService.deleteOverride(overrideId, workspaceId);
        return true;
    }

    private RecurringItemOverrideResponse toOverrideResponse(RecurringItemOverride override) {
        return RecurringItemOverrideResponse.builder()
                .id(override.getId())
                .recurringItemId(override.getRecurringItemId())
                .occurrenceDate(override.getOccurrenceDate())
                .amount(override.getAmount())
                .notes(override.getNotes())
                .createdAt(override.getCreatedAt())
                .createdBy(override.getCreatedBy())
                .updatedAt(override.getUpdatedAt())
                .updatedBy(override.getUpdatedBy())
                .build();
    }

    private void applySpecifiedFlags(UpdateRecurringItemInput input, Map<String, Object> rawInput) {
        if (rawInput.containsKey("categoryId")) {
            input.setCategoryIdSpecified(true);
        }
        if (rawInput.containsKey("notes")) {
            input.setNotesSpecified(true);
        }
        if (rawInput.containsKey("endDate")) {
            input.setEndDateSpecified(true);
        }
        if (rawInput.containsKey("tagIds")) {
            input.setTagIdsSpecified(true);
        }
    }
}
