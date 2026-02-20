package com.dripl.transaction.split.mapper;

import com.dripl.transaction.entity.Transaction;
import com.dripl.transaction.split.dto.TransactionSplitDto;
import com.dripl.transaction.split.entity.TransactionSplit;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface TransactionSplitMapper {

    @Mapping(target = "transactionIds", ignore = true)
    TransactionSplitDto toDto(TransactionSplit split);

    default TransactionSplitDto toDto(TransactionSplit split, List<Transaction> transactions) {
        TransactionSplitDto dto = toDto(split);
        return dto.toBuilder()
                .transactionIds(transactions.stream()
                        .map(Transaction::getId)
                        .collect(Collectors.toSet()))
                .build();
    }
}
