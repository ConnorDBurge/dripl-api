package com.balanced.account.mapper;

import com.balanced.account.dto.AccountResponse;
import com.balanced.account.dto.UpdateAccountInput;
import com.balanced.account.entity.Account;
import com.balanced.common.enums.Status;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.time.LocalDateTime;
import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface AccountMapper {

    AccountResponse toDto(Account account);

    List<AccountResponse> toDtos(List<Account> accounts);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "workspaceId", ignore = true)
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "balance", ignore = true)
    @Mapping(target = "balanceLastUpdated", ignore = true)
    @Mapping(target = "closedAt", ignore = true)
    @Mapping(target = "externalId", ignore = true)
    @Mapping(target = "bankConnectionId", ignore = true)
    void updateEntity(UpdateAccountInput dto, @MappingTarget Account account);

    @AfterMapping
    default void handleSideEffects(UpdateAccountInput dto, @MappingTarget Account account) {
        if (dto.getStatus() != null) {
            if (dto.getStatus() == Status.CLOSED && account.getClosedAt() == null) {
                account.setClosedAt(LocalDateTime.now());
            } else if (dto.getStatus() == Status.ACTIVE) {
                account.setClosedAt(null);
            }
        }
    }
}
