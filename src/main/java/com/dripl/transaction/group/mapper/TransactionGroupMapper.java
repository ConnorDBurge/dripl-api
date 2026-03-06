package com.dripl.transaction.group.mapper;

import com.dripl.transaction.entity.Transaction;
import com.dripl.transaction.group.dto.TransactionGroupResponse;
import com.dripl.transaction.group.entity.TransactionGroup;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface TransactionGroupMapper {

    @Mapping(target = "totalAmount", ignore = true)
    @Mapping(target = "transactionIds", ignore = true)
    TransactionGroupResponse toDto(TransactionGroup group);

    default TransactionGroupResponse toDto(TransactionGroup group, List<Transaction> transactions) {
        TransactionGroupResponse dto = toDto(group);
        return dto.toBuilder()
                .totalAmount(transactions.stream()
                        .map(Transaction::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .transactionIds(transactions.stream()
                        .map(Transaction::getId)
                        .collect(Collectors.toSet()))
                .build();
    }

    default List<TransactionGroupResponse> toDtos(
            List<TransactionGroup> groups,
            java.util.function.BiFunction<UUID, UUID, List<Transaction>> transactionsFetcher,
            UUID workspaceId) {
        return groups.stream()
                .map(group -> toDto(group, transactionsFetcher.apply(group.getId(), workspaceId)))
                .toList();
    }
}
