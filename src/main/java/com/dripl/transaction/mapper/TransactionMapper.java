package com.dripl.transaction.mapper;

import com.dripl.transaction.dto.TransactionDto;
import com.dripl.transaction.dto.UpdateTransactionDto;
import com.dripl.transaction.entity.Transaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface TransactionMapper {

    TransactionDto toDto(Transaction transaction);

    List<TransactionDto> toDtos(List<Transaction> transactions);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "workspaceId", ignore = true)
    @Mapping(target = "accountId", ignore = true)
    @Mapping(target = "merchantId", ignore = true)
    @Mapping(target = "categoryId", ignore = true)
    @Mapping(target = "notes", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "pendingAt", ignore = true)
    @Mapping(target = "postedAt", ignore = true)
    @Mapping(target = "recurringItemId", ignore = true)
    @Mapping(target = "groupId", ignore = true)
    @Mapping(target = "tagIds", ignore = true)
    void updateEntity(UpdateTransactionDto dto, @MappingTarget Transaction transaction);
}
