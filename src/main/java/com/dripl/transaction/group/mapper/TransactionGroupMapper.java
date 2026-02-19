package com.dripl.transaction.group.mapper;

import com.dripl.transaction.entity.Transaction;
import com.dripl.transaction.group.dto.TransactionGroupDto;
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
    TransactionGroupDto toDto(TransactionGroup group);

    default TransactionGroupDto toDto(TransactionGroup group, List<Transaction> transactions) {
        TransactionGroupDto dto = toDto(group);
        return dto.toBuilder()
                .totalAmount(transactions.stream()
                        .map(Transaction::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .transactionIds(transactions.stream()
                        .map(Transaction::getId)
                        .collect(Collectors.toSet()))
                .build();
    }
}
