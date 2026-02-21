package com.dripl.account.mapper;

import com.dripl.account.dto.AccountDto;
import com.dripl.account.dto.UpdateAccountDto;
import com.dripl.account.entity.Account;
import com.dripl.common.enums.Status;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.time.LocalDateTime;
import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface AccountMapper {

    AccountDto toDto(Account account);

    List<AccountDto> toDtos(List<Account> accounts);

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
    void updateEntity(UpdateAccountDto dto, @MappingTarget Account account);

    @AfterMapping
    default void handleSideEffects(UpdateAccountDto dto, @MappingTarget Account account) {
        if (dto.getStatus() != null) {
            if (dto.getStatus() == Status.CLOSED && account.getClosedAt() == null) {
                account.setClosedAt(LocalDateTime.now());
            } else if (dto.getStatus() == Status.ACTIVE) {
                account.setClosedAt(null);
            }
        }
    }
}
