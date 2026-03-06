package com.dripl.transaction.resolver;

import com.dripl.common.dto.PagedResponse;
import com.dripl.common.graphql.GraphQLContext;
import com.dripl.transaction.dto.CreateTransactionInput;
import com.dripl.transaction.dto.TransactionFilter;
import com.dripl.transaction.dto.TransactionResponse;
import com.dripl.transaction.dto.TransactionSort;
import com.dripl.transaction.dto.UpdateTransactionInput;
import com.dripl.transaction.entity.Transaction;
import com.dripl.transaction.event.dto.TransactionEventResponse;
import com.dripl.transaction.event.service.TransactionEventService;
import com.dripl.transaction.mapper.TransactionMapper;
import com.dripl.transaction.repository.TransactionSpecifications;
import com.dripl.transaction.service.TransactionService;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.dripl.transaction.repository.TransactionSpecifications.optionally;

@Controller
@RequiredArgsConstructor
public class TransactionResolver {

    private static final int MAX_PAGE_SIZE = 250;

    private final TransactionService transactionService;
    private final TransactionEventService transactionEventService;
    private final TransactionMapper transactionMapper;

    @PreAuthorize("hasAuthority('READ')")
    @QueryMapping
    public PagedResponse<TransactionResponse> transactions(
            @Argument TransactionFilter filter,
            @Argument TransactionSort sort,
            @Argument Integer page,
            @Argument Integer size) {

        UUID workspaceId = GraphQLContext.workspaceId();
        int pageNum = page != null ? page : 0;
        int pageSize = Math.min(Math.max(size != null ? size : 25, 1), MAX_PAGE_SIZE);

        Specification<Transaction> spec = buildSpec(workspaceId, filter);

        String sortBy = sort != null && sort.getSortBy() != null ? sort.getSortBy() : "date";
        Sort.Direction sortDirection = sort != null && sort.getSortDirection() != null
                ? Sort.Direction.valueOf(sort.getSortDirection())
                : Sort.Direction.DESC;

        Pageable pageable;
        if (TransactionSpecifications.isRelatedSort(sortBy)) {
            spec = spec.and(TransactionSpecifications.sortByRelatedName(sortBy, sortDirection));
            pageable = PageRequest.of(pageNum, pageSize);
        } else {
            pageable = PageRequest.of(pageNum, pageSize, TransactionSpecifications.buildSort(sortBy, sortDirection));
        }

        Page<TransactionResponse> result = transactionService.listAll(spec, pageable)
                .map(transactionMapper::toDto);

        return PagedResponse.from(result);
    }

    @PreAuthorize("hasAuthority('READ')")
    @QueryMapping
    public TransactionResponse transaction(@Argument UUID transactionId) {
        UUID workspaceId = GraphQLContext.workspaceId();
        return transactionMapper.toDto(transactionService.getTransaction(transactionId, workspaceId));
    }

    @PreAuthorize("hasAuthority('READ')")
    @QueryMapping
    public List<TransactionEventResponse> transactionEvents(@Argument UUID transactionId) {
        UUID workspaceId = GraphQLContext.workspaceId();
        transactionService.getTransaction(transactionId, workspaceId);
        return transactionEventService.getEventsForTransaction(transactionId, workspaceId);
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @MutationMapping
    public TransactionResponse createTransaction(@Argument @Valid CreateTransactionInput input) {
        UUID workspaceId = GraphQLContext.workspaceId();
        return transactionMapper.toDto(transactionService.createTransaction(workspaceId, input));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @MutationMapping
    @SuppressWarnings("unchecked")
    public TransactionResponse updateTransaction(
            @Argument UUID transactionId, @Argument @Valid UpdateTransactionInput input,
            DataFetchingEnvironment env) {
        UUID workspaceId = GraphQLContext.workspaceId();

        Map<String, Object> rawInput = env.getArgument("input");
        if (rawInput != null) {
            applySpecifiedFlags(input, rawInput);
        }

        return transactionMapper.toDto(transactionService.updateTransaction(transactionId, workspaceId, input));
    }

    @PreAuthorize("hasAuthority('DELETE')")
    @MutationMapping
    public boolean deleteTransaction(@Argument UUID transactionId) {
        UUID workspaceId = GraphQLContext.workspaceId();
        transactionService.deleteTransaction(transactionId, workspaceId);
        return true;
    }

    private Specification<Transaction> buildSpec(UUID workspaceId, TransactionFilter filter) {
        Specification<Transaction> spec = Specification.where(TransactionSpecifications.inWorkspace(workspaceId));
        if (filter == null) return spec;

        LocalDateTime start = filter.getStartDate() != null
                ? LocalDate.parse(filter.getStartDate()).atStartOfDay() : null;
        LocalDateTime end = filter.getEndDate() != null
                ? LocalDate.parse(filter.getEndDate()).atTime(23, 59, 59) : null;

        return spec
                .and(optionally(filter.getAccountId(),        TransactionSpecifications::hasAccount))
                .and(optionally(filter.getMerchantId(),       TransactionSpecifications::hasMerchant))
                .and(optionally(filter.getGroupId(),          TransactionSpecifications::hasGroup))
                .and(optionally(filter.getSplitId(),          TransactionSpecifications::hasSplit))
                .and(optionally(filter.getCategoryId(),       TransactionSpecifications::hasCategory))
                .and(optionally(filter.getRecurringItemId(),  TransactionSpecifications::hasRecurringItem))
                .and(optionally(filter.getStatus(),           TransactionSpecifications::hasStatus))
                .and(optionally(filter.getSource(),           TransactionSpecifications::hasSource))
                .and(optionally(filter.getCurrencyCode(),     TransactionSpecifications::hasCurrency))
                .and(optionally(filter.getTagIds(),           TransactionSpecifications::hasAnyTag))
                .and(optionally(filter.getSearch(),           TransactionSpecifications::searchText))
                .and(optionally(filter.getMinAmount(),        TransactionSpecifications::amountGreaterThanOrEqual))
                .and(optionally(filter.getMaxAmount(),        TransactionSpecifications::amountLessThanOrEqual))
                .and(optionally(start,                        TransactionSpecifications::dateOnOrAfter))
                .and(optionally(end,                          TransactionSpecifications::dateOnOrBefore));
    }

    private void applySpecifiedFlags(UpdateTransactionInput input, Map<String, Object> rawInput) {
        if (rawInput.containsKey("merchantId") && !input.isMerchantIdSpecified()) {
            input.setMerchantId(null);
        }
        if (rawInput.containsKey("categoryId") && !input.isCategoryIdSpecified()) {
            input.setCategoryId(null);
        }
        if (rawInput.containsKey("notes") && !input.isNotesSpecified()) {
            input.setNotes(null);
        }
        if (rawInput.containsKey("tagIds") && !input.isTagIdsSpecified()) {
            input.setTagIds(null);
        }
        if (rawInput.containsKey("groupId") && !input.isGroupIdSpecified()) {
            input.setGroupId(null);
        }
        if (rawInput.containsKey("splitId") && !input.isSplitIdSpecified()) {
            input.setSplitId(null);
        }
        if (rawInput.containsKey("recurringItemId") && !input.isRecurringItemIdSpecified()) {
            input.setRecurringItemId(null);
        }
        if (rawInput.containsKey("occurrenceDate") && !input.isOccurrenceDateSpecified()) {
            input.setOccurrenceDate(null);
        }
    }
}
